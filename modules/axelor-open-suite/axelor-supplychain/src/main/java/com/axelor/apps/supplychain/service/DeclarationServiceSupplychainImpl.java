/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2023 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.supplychain.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.declaration.DeclarationComputeService;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.sale.service.declaration.DeclarationMarginService;
import com.axelor.apps.sale.service.declaration.DeclarationServiceImpl;
import com.axelor.apps.stock.db.ShipmentMode;
import com.axelor.apps.stock.db.StockConfig;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.PartnerStockSettingsService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.CustomerShippingCarriagePaid;
import com.axelor.apps.supplychain.db.PartnerSupplychainLink;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.db.repo.PartnerSupplychainLinkTypeRepository;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.studio.db.AppSupplychain;
import com.google.common.base.MoreObjects;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class DeclarationServiceSupplychainImpl extends DeclarationServiceImpl
    implements DeclarationSupplychainService {

  protected AppSupplychainService appSupplychainService;
  protected DeclarationStockService declarationStockService;
  protected PartnerStockSettingsService partnerStockSettingsService;
  protected StockConfigService stockConfigService;

  @Inject
  public DeclarationServiceSupplychainImpl(
      DeclarationLineService declarationLineService,
      AppBaseService appBaseService,
      DeclarationLineRepository declarationLineRepo,
      DeclarationRepository declarationRepo,
      DeclarationComputeService declarationComputeService,
      DeclarationMarginService declarationMarginService,
      AppSupplychainService appSupplychainService,
      DeclarationStockService declarationStockService,
      PartnerStockSettingsService partnerStockSettingsService,
      StockConfigService stockConfigService) {
    super(
        declarationLineService,
        appBaseService,
        declarationLineRepo,
        declarationRepo,
        declarationComputeService,
        declarationMarginService);
    this.appSupplychainService = appSupplychainService;
    this.declarationStockService = declarationStockService;
    this.partnerStockSettingsService = partnerStockSettingsService;
    this.stockConfigService = stockConfigService;
  }

  public Declaration getClientInformations(Declaration declaration) {
    Partner client = declaration.getClientPartner();
    PartnerService partnerService = Beans.get(PartnerService.class);
    if (client != null) {
      declaration.setPaymentCondition(client.getPaymentCondition());
      declaration.setPaymentMode(client.getInPaymentMode());
      declaration.setMainInvoicingAddress(partnerService.getInvoicingAddress(client));
      this.computeAddressStr(declaration);
      declaration.setDeliveryAddress(partnerService.getDeliveryAddress(client));
      declaration.setPriceList(
          Beans.get(PartnerPriceListService.class)
              .getDefaultPriceList(client, PriceListRepository.TYPE_SALE));
    }
    return declaration;
  }

  @Override
  public void updateAmountToBeSpreadOverTheTimetable(Declaration declaration) {
    List<Timetable> timetableList = declaration.getTimetableList();
    BigDecimal totalHT = declaration.getExTaxTotal();
    BigDecimal sumTimetableAmount = BigDecimal.ZERO;
    if (timetableList != null) {
      for (Timetable timetable : timetableList) {
        sumTimetableAmount = sumTimetableAmount.add(timetable.getAmount());
      }
    }
    declaration.setAmountToBeSpreadOverTheTimetable(totalHT.subtract(sumTimetableAmount));
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public boolean enableEditOrder(Declaration declaration) throws AxelorException {
    boolean checkAvailabiltyRequest = super.enableEditOrder(declaration);
    AppSupplychain appSupplychain = appSupplychainService.getAppSupplychain();

    if (!appSupplychainService.isApp("supplychain")) {
      return checkAvailabiltyRequest;
    }

    List<StockMove> allStockMoves =
        Beans.get(StockMoveRepository.class)
            .findAllByDeclarationAndStatus(
                StockMoveRepository.ORIGIN_SALE_ORDER,
                declaration.getId(),
                StockMoveRepository.STATUS_PLANNED)
            .fetch();
    List<StockMove> stockMoves =
        !allStockMoves.isEmpty()
            ? allStockMoves.stream()
                .filter(stockMove -> !stockMove.getAvailabilityRequest())
                .collect(Collectors.toList())
            : allStockMoves;
    checkAvailabiltyRequest =
        stockMoves.size() != allStockMoves.size() ? true : checkAvailabiltyRequest;
    if (!stockMoves.isEmpty()) {
      StockMoveService stockMoveService = Beans.get(StockMoveService.class);
      CancelReason cancelReason = appSupplychain.getCancelReasonOnChangingDeclaration();
      if (cancelReason == null) {
        throw new AxelorException(
            appSupplychain,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            SupplychainExceptionMessage.SUPPLYCHAIN_MISSING_CANCEL_REASON_ON_CHANGING_SALE_ORDER);
      }
      for (StockMove stockMove : stockMoves) {
        stockMoveService.cancel(stockMove, cancelReason);
        stockMove.setArchived(true);
        for (StockMoveLine stockMoveline : stockMove.getStockMoveLineList()) {
          stockMoveline.setDeclarationLine(null);
          stockMoveline.setArchived(true);
        }
      }
    }
    return checkAvailabiltyRequest;
  }

  /**
   * In the supplychain implementation, we check if the user has deleted already delivered qty.
   *
   * @param declaration
   * @param declarationView
   * @throws AxelorException if the user tried to remove already delivered qty.
   */
  @Override
  public void checkModifiedConfirmedOrder(Declaration declaration, Declaration declarationView)
      throws AxelorException {

    if (!appSupplychainService.isApp("supplychain")) {
      super.checkModifiedConfirmedOrder(declaration, declarationView);
      return;
    }

    List<DeclarationLine> declarationLineList =
        MoreObjects.firstNonNull(declaration.getDeclarationLineList(), Collections.emptyList());
    List<DeclarationLine> declarationViewLineList =
        MoreObjects.firstNonNull(declarationView.getDeclarationLineList(), Collections.emptyList());

    for (DeclarationLine declarationLine : declarationLineList) {
      if (declarationLine.getDeliveryState()
          <= DeclarationLineRepository.DELIVERY_STATE_NOT_DELIVERED) {
        continue;
      }

      Optional<DeclarationLine> optionalNewDeclarationLine =
          declarationViewLineList.stream().filter(declarationLine::equals).findFirst();

      if (optionalNewDeclarationLine.isPresent()) {
        DeclarationLine newDeclarationLine = optionalNewDeclarationLine.get();

        if (newDeclarationLine.getQty().compareTo(declarationLine.getDeliveredQty()) < 0) {
          throw new AxelorException(
              declaration,
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(SupplychainExceptionMessage.SO_CANT_DECREASE_QTY_ON_DELIVERED_LINE),
              declarationLine.getFullName());
        }
      } else {
        throw new AxelorException(
            declaration,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(SupplychainExceptionMessage.SO_CANT_REMOVED_DELIVERED_LINE),
            declarationLine.getFullName());
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validateChanges(Declaration declaration) throws AxelorException {
    super.validateChanges(declaration);

    if (!appSupplychainService.isApp("supplychain")) {
      return;
    }

    declarationStockService.fullyUpdateDeliveryState(declaration);
    declaration.setOrderBeingEdited(false);

    if (appSupplychainService.getAppSupplychain().getCustomerStockMoveGenerationAuto()) {
      declarationStockService.createStocksMovesFromDeclaration(declaration);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateToConfirmedStatus(Declaration declaration) throws AxelorException {
    if (declaration.getStatusSelect() == null
        || declaration.getStatusSelect() != DeclarationRepository.STATUS_ORDER_COMPLETED) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SupplychainExceptionMessage.SALE_ORDER_BACK_TO_CONFIRMED_WRONG_STATUS));
    }
    declaration.setStatusSelect(DeclarationRepository.STATUS_ORDER_CONFIRMED);
    declarationRepo.save(declaration);
  }

  @Override
  public String createShipmentCostLine(Declaration declaration) throws AxelorException {
    List<DeclarationLine> declarationLines = declaration.getDeclarationLineList();
    Partner client = declaration.getClientPartner();
    ShipmentMode shipmentMode = declaration.getShipmentMode();

    if (shipmentMode == null) {
      return null;
    }
    Product shippingCostProduct = shipmentMode.getShippingCostsProduct();
    if (shippingCostProduct == null) {
      return null;
    }
    BigDecimal carriagePaidThreshold = shipmentMode.getCarriagePaidThreshold();
    if (client != null) {
      List<CustomerShippingCarriagePaid> carriagePaids =
          client.getCustomerShippingCarriagePaidList();
      for (CustomerShippingCarriagePaid customerShippingCarriagePaid : carriagePaids) {
        if (shipmentMode.getId() == customerShippingCarriagePaid.getShipmentMode().getId()) {
          if (customerShippingCarriagePaid.getShippingCostsProduct() != null) {
            shippingCostProduct = customerShippingCarriagePaid.getShippingCostsProduct();
          }
          carriagePaidThreshold = customerShippingCarriagePaid.getCarriagePaidThreshold();
          break;
        }
      }
    }
    if (carriagePaidThreshold != null && shipmentMode.getHasCarriagePaidPossibility()) {
      if (computeExTaxTotalWithoutShippingLines(declaration).compareTo(carriagePaidThreshold) >= 0) {
        String message = removeShipmentCostLine(declaration);
        declarationComputeService.computeDeclaration(declaration);
        declarationMarginService.computeMarginDeclaration(declaration);
        return message;
      }
    }
    if (alreadyHasShippingCostLine(declaration, shippingCostProduct)) {
      return null;
    }
    DeclarationLine shippingCostLine = createShippingCostLine(declaration, shippingCostProduct);
    declarationLines.add(shippingCostLine);
    declarationComputeService.computeDeclaration(declaration);
    declarationMarginService.computeMarginDeclaration(declaration);
    return null;
  }

  @Override
  public boolean alreadyHasShippingCostLine(Declaration declaration, Product shippingCostProduct) {
    List<DeclarationLine> declarationLines = declaration.getDeclarationLineList();
    if (declarationLines == null) {
      return false;
    }
    for (DeclarationLine declarationLine : declarationLines) {
      if (shippingCostProduct.equals(declarationLine.getProduct())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public DeclarationLine createShippingCostLine(Declaration declaration, Product shippingCostProduct)
      throws AxelorException {
    DeclarationLine shippingCostLine = new DeclarationLine();
    shippingCostLine.setDeclaration(declaration);
    shippingCostLine.setProduct(shippingCostProduct);
    declarationLineService.computeProductInformation(shippingCostLine, declaration);
    declarationLineService.computeValues(declaration, shippingCostLine);
    return shippingCostLine;
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public String removeShipmentCostLine(Declaration declaration) {
    List<DeclarationLine> declarationLines = declaration.getDeclarationLineList();
    if (declarationLines == null) {
      return null;
    }
    List<DeclarationLine> linesToRemove = new ArrayList<>();
    for (DeclarationLine declarationLine : declarationLines) {
      if (declarationLine.getProduct().getIsShippingCostsProduct()) {
        linesToRemove.add(declarationLine);
      }
    }
    if (linesToRemove.isEmpty()) {
      return null;
    }
    for (DeclarationLine lineToRemove : linesToRemove) {
      declarationLines.remove(lineToRemove);
      if (lineToRemove.getId() != null) {
        declarationLineRepo.remove(lineToRemove);
      }
    }
    declaration.setDeclarationLineList(declarationLines);
    return I18n.get("Carriage paid threshold is exceeded, all shipment cost lines are removed");
  }

  @Override
  public BigDecimal computeExTaxTotalWithoutShippingLines(Declaration declaration) {
    List<DeclarationLine> declarationLines = declaration.getDeclarationLineList();
    if (declarationLines == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal exTaxTotal = BigDecimal.ZERO;
    for (DeclarationLine declarationLine : declarationLines) {
      if (!declarationLine.getProduct().getIsShippingCostsProduct()) {
        exTaxTotal = exTaxTotal.add(declarationLine.getExTaxTotal());
      }
    }
    return exTaxTotal;
  }

  public void setDefaultInvoicedAndDeliveredPartnersAndAddresses(Declaration declaration) {
    if (declaration != null
        && declaration.getClientPartner() != null
        && declaration.getClientPartner().getId() != null) {
      Partner clientPartner =
          Beans.get(PartnerRepository.class).find(declaration.getClientPartner().getId());
      if (clientPartner != null) {
        setDefaultInvoicedAndDeliveredPartners(declaration, clientPartner);
        setInvoicedAndDeliveredAddresses(declaration);
      }
    }
  }

  protected void setInvoicedAndDeliveredAddresses(Declaration declaration) {
    if (declaration.getInvoicedPartner() != null) {
      declaration.setMainInvoicingAddress(
          Beans.get(PartnerService.class).getInvoicingAddress(declaration.getInvoicedPartner()));
      declaration.setMainInvoicingAddressStr(
          Beans.get(AddressService.class).computeAddressStr(declaration.getMainInvoicingAddress()));
    }
    if (declaration.getDeliveredPartner() != null) {
      declaration.setDeliveryAddress(
          Beans.get(PartnerService.class).getDeliveryAddress(declaration.getDeliveredPartner()));
      declaration.setDeliveryAddressStr(
          Beans.get(AddressService.class).computeAddressStr(declaration.getDeliveryAddress()));
    }
  }

  protected void setDefaultInvoicedAndDeliveredPartners(
      Declaration declaration, Partner clientPartner) {
    if (!CollectionUtils.isEmpty(clientPartner.getPartner1SupplychainLinkList())) {
      List<PartnerSupplychainLink> partnerSupplychainLinkList =
          clientPartner.getPartner1SupplychainLinkList();
      // Retrieve all Invoiced by Type
      List<PartnerSupplychainLink> partnerSupplychainLinkInvoicedByList =
          partnerSupplychainLinkList.stream()
              .filter(
                  partnerSupplychainLink ->
                      PartnerSupplychainLinkTypeRepository.TYPE_SELECT_INVOICED_BY.equals(
                          partnerSupplychainLink.getPartnerSupplychainLinkType().getTypeSelect()))
              .collect(Collectors.toList());
      // Retrieve all Delivered by Type
      List<PartnerSupplychainLink> partnerSupplychainLinkDeliveredByList =
          partnerSupplychainLinkList.stream()
              .filter(
                  partnerSupplychainLink ->
                      PartnerSupplychainLinkTypeRepository.TYPE_SELECT_DELIVERED_BY.equals(
                          partnerSupplychainLink.getPartnerSupplychainLinkType().getTypeSelect()))
              .collect(Collectors.toList());

      // If there is only one, then it is the default one
      if (partnerSupplychainLinkInvoicedByList.size() == 1) {
        PartnerSupplychainLink partnerSupplychainLinkInvoicedBy =
            partnerSupplychainLinkInvoicedByList.get(0);
        declaration.setInvoicedPartner(partnerSupplychainLinkInvoicedBy.getPartner2());
      } else if (partnerSupplychainLinkInvoicedByList.isEmpty()) {
        declaration.setInvoicedPartner(clientPartner);
      } else {
        declaration.setInvoicedPartner(null);
      }
      if (partnerSupplychainLinkDeliveredByList.size() == 1) {
        PartnerSupplychainLink partnerSupplychainLinkDeliveredBy =
            partnerSupplychainLinkDeliveredByList.get(0);
        declaration.setDeliveredPartner(partnerSupplychainLinkDeliveredBy.getPartner2());
      } else if (partnerSupplychainLinkDeliveredByList.isEmpty()) {
        declaration.setDeliveredPartner(clientPartner);
      } else {
        declaration.setDeliveredPartner(null);
      }

    } else {
      declaration.setInvoicedPartner(clientPartner);
      declaration.setDeliveredPartner(clientPartner);
    }
  }

  @Override
  public StockLocation getStockLocation(Partner clientPartner, Company company)
      throws AxelorException {
    if (company == null) {
      return null;
    }
    StockLocation stockLocation =
        partnerStockSettingsService.getDefaultStockLocation(
            clientPartner, company, StockLocation::getUsableOnDeclaration);
    if (stockLocation == null) {
      StockConfig stockConfig = stockConfigService.getStockConfig(company);
      stockLocation = stockConfigService.getPickupDefaultStockLocation(stockConfig);
    }
    return stockLocation;
  }

  @Override
  public StockLocation getToStockLocation(Partner clientPartner, Company company)
      throws AxelorException {
    if (company == null) {
      return null;
    }
    StockLocation toStockLocation =
        partnerStockSettingsService.getDefaultExternalStockLocation(
            clientPartner, company, StockLocation::getUsableOnDeclaration);
    if (toStockLocation == null) {
      StockConfig stockConfig = stockConfigService.getStockConfig(company);
      toStockLocation = stockConfigService.getCustomerVirtualStockLocation(stockConfig);
    }
    return toStockLocation;
  }
}
