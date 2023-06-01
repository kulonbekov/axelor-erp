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

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.stock.db.PartnerStockSettings;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.PartnerStockSettingsService;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.db.repo.SupplyChainConfigRepository;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeclarationStockServiceImpl implements DeclarationStockService {

  protected StockMoveService stockMoveService;
  protected StockMoveLineService stockMoveLineService;
  protected StockConfigService stockConfigService;
  protected UnitConversionService unitConversionService;
  protected DeclarationLineServiceSupplyChain declarationLineServiceSupplyChain;
  protected StockMoveLineServiceSupplychain stockMoveLineSupplychainService;
  protected StockMoveLineRepository stockMoveLineRepository;
  protected AppBaseService appBaseService;
  protected DeclarationRepository declarationRepository;
  protected AppSupplychainService appSupplychainService;
  protected SupplyChainConfigService supplyChainConfigService;
  protected ProductCompanyService productCompanyService;
  protected PartnerStockSettingsService partnerStockSettingsService;

  @Inject
  public DeclarationStockServiceImpl(
      StockMoveService stockMoveService,
      StockMoveLineService stockMoveLineService,
      StockConfigService stockConfigService,
      UnitConversionService unitConversionService,
      DeclarationLineServiceSupplyChain declarationLineServiceSupplyChain,
      StockMoveLineServiceSupplychain stockMoveLineSupplychainService,
      StockMoveLineRepository stockMoveLineRepository,
      AppBaseService appBaseService,
      DeclarationRepository declarationRepository,
      AppSupplychainService appSupplychainService,
      SupplyChainConfigService supplyChainConfigService,
      ProductCompanyService productCompanyService,
      PartnerStockSettingsService partnerStockSettingsService) {
    this.stockMoveService = stockMoveService;
    this.stockMoveLineService = stockMoveLineService;
    this.stockConfigService = stockConfigService;
    this.unitConversionService = unitConversionService;
    this.declarationLineServiceSupplyChain = declarationLineServiceSupplyChain;
    this.stockMoveLineSupplychainService = stockMoveLineSupplychainService;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.appBaseService = appBaseService;
    this.declarationRepository = declarationRepository;
    this.appSupplychainService = appSupplychainService;
    this.supplyChainConfigService = supplyChainConfigService;
    this.productCompanyService = productCompanyService;
    this.partnerStockSettingsService = partnerStockSettingsService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public List<Long> createStocksMovesFromDeclaration(Declaration declaration) throws AxelorException {

    if (!this.isDeclarationWithProductsToDeliver(declaration)) {
      return null;
    }

    if (declaration.getStockLocation() == null) {
      throw new AxelorException(
          declaration,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(SupplychainExceptionMessage.SO_MISSING_STOCK_LOCATION),
          declaration.getDeclarationSeq());
    }

    List<Long> stockMoveList = new ArrayList<>();

    Map<LocalDate, List<DeclarationLine>> declarationLinePerDateMap =
        getAllDeclarationLinePerDate(declaration);

    for (LocalDate estimatedDeliveryDate :
        declarationLinePerDateMap.keySet().stream()
            .filter(Objects::nonNull)
            .sorted((x, y) -> x.compareTo(y))
            .collect(Collectors.toList())) {

      List<DeclarationLine> declarationLineList = declarationLinePerDateMap.get(estimatedDeliveryDate);

      Optional<StockMove> stockMove =
          createStockMove(declaration, estimatedDeliveryDate, declarationLineList);

      stockMove.map(StockMove::getId).ifPresent(stockMoveList::add);
    }
    Optional<List<DeclarationLine>> declarationLineList =
        Optional.ofNullable(declarationLinePerDateMap.get(null));
    if (declarationLineList.isPresent()) {

      Optional<StockMove> stockMove = createStockMove(declaration, null, declarationLineList.get());

      stockMove.map(StockMove::getId).ifPresent(stockMoveList::add);
    }
    return stockMoveList;
  }

  protected Optional<StockMove> createStockMove(
      Declaration declaration, LocalDate estimatedDeliveryDate, List<DeclarationLine> declarationLineList)
      throws AxelorException {

    StockMove stockMove =
        this.createStockMove(declaration, declaration.getCompany(), estimatedDeliveryDate);
    stockMove.setDeliveryCondition(declaration.getDeliveryCondition());

    for (DeclarationLine declarationLine : declarationLineList) {
      if (declarationLine.getProduct() != null) {
        BigDecimal qty = declarationLineServiceSupplyChain.computeUndeliveredQty(declarationLine);
        if (qty.signum() > 0 && !existActiveStockMoveForDeclarationLine(declarationLine)) {
          createStockMoveLine(stockMove, declarationLine, qty);
        }
      }
    }

    if (stockMove.getStockMoveLineList() == null || stockMove.getStockMoveLineList().isEmpty()) {
      return Optional.empty();
    }

    if (stockMove.getStockMoveLineList().stream()
        .noneMatch(
            stockMoveLine ->
                stockMoveLine.getDeclarationLine() != null
                    && stockMoveLine.getDeclarationLine().getTypeSelect()
                        == DeclarationLineRepository.TYPE_NORMAL)) {
      stockMove.setFullySpreadOverLogisticalFormsFlag(true);
    }

    boolean isNeedingConformityCertificate = declaration.getIsNeedingConformityCertificate();
    stockMove.setIsNeedingConformityCertificate(isNeedingConformityCertificate);

    if (isNeedingConformityCertificate) {
      stockMove.setSignatoryUser(
          stockConfigService.getStockConfig(stockMove.getCompany()).getSignatoryUser());
    }

    SupplyChainConfig supplychainConfig =
        supplyChainConfigService.getSupplyChainConfig(declaration.getCompany());

    if (supplychainConfig.getDefaultEstimatedDate() != null
        && supplychainConfig.getDefaultEstimatedDate() == SupplyChainConfigRepository.CURRENT_DATE
        && stockMove.getEstimatedDate() == null) {
      stockMove.setEstimatedDate(appBaseService.getTodayDate(declaration.getCompany()));
    } else if (supplychainConfig.getDefaultEstimatedDate()
            == SupplyChainConfigRepository.CURRENT_DATE_PLUS_DAYS
        && stockMove.getEstimatedDate() == null) {
      stockMove.setEstimatedDate(
          appBaseService
              .getTodayDate(declaration.getCompany())
              .plusDays(supplychainConfig.getNumberOfDays().longValue()));
    }

    setReservationDateTime(stockMove, declaration);
    stockMoveService.plan(stockMove);

    return Optional.of(stockMove);
  }

  protected Map<LocalDate, List<DeclarationLine>> getAllDeclarationLinePerDate(Declaration declaration) {

    Map<LocalDate, List<DeclarationLine>> declarationLinePerDateMap = new HashMap<>();

    for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {

      if (declarationLineServiceSupplyChain.computeUndeliveredQty(declarationLine).signum() <= 0) {
        continue;
      }

      LocalDate dateKey = declarationLine.getEstimatedShippingDate();

      if (dateKey == null) {
        dateKey = declarationLine.getDeclaration().getEstimatedShippingDate();
      }
      if (dateKey == null) {
        dateKey = declarationLine.getDesiredDeliveryDate();
      }

      List<DeclarationLine> declarationLineLists = declarationLinePerDateMap.get(dateKey);

      if (declarationLineLists == null) {
        declarationLineLists = new ArrayList<>();
        declarationLinePerDateMap.put(dateKey, declarationLineLists);
      }

      declarationLineLists.add(declarationLine);
    }

    return declarationLinePerDateMap;
  }

  protected boolean isDeclarationWithProductsToDeliver(Declaration declaration) throws AxelorException {

    if (declaration.getDeclarationLineList() == null) {
      return false;
    }

    for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {

      if (this.isStockMoveProduct(declarationLine)) {

        return true;
      }
    }
    return false;
  }

  @Override
  public StockMove createStockMove(
      Declaration declaration, Company company, LocalDate estimatedDeliveryDate)
      throws AxelorException {
    StockLocation toStockLocation = declaration.getToStockLocation();
    if (toStockLocation == null) {
      toStockLocation =
          partnerStockSettingsService.getDefaultExternalStockLocation(
              declaration.getClientPartner(), company, null);
    }
    if (toStockLocation == null) {
      toStockLocation =
          stockConfigService.getCustomerVirtualStockLocation(
              stockConfigService.getStockConfig(company));
    }

    Partner partner = computePartnerToUseForStockMove(declaration);

    StockMove stockMove =
        stockMoveService.createStockMove(
            null,
            declaration.getDeliveryAddress(),
            company,
            partner,
            declaration.getStockLocation(),
            toStockLocation,
            null,
            estimatedDeliveryDate,
            declaration.getDescription(),
            declaration.getShipmentMode(),
            declaration.getFreightCarrierMode(),
            declaration.getCarrierPartner(),
            declaration.getForwarderPartner(),
            declaration.getIncoterm(),
            StockMoveRepository.TYPE_OUTGOING);

    stockMove.setToAddressStr(declaration.getDeliveryAddressStr());
    stockMove.setOriginId(declaration.getId());
    stockMove.setOriginTypeSelect(StockMoveRepository.ORIGIN_SALE_ORDER);
    stockMove.setOrigin(declaration.getDeclarationSeq());
    stockMove.setStockMoveLineList(new ArrayList<>());
    stockMove.setTradingName(declaration.getTradingName());
    stockMove.setSpecificPackage(declaration.getSpecificPackage());
    stockMove.setNote(declaration.getDeliveryComments());
    stockMove.setPickingOrderComments(declaration.getPickingOrderComments());
    stockMove.setGroupProductsOnPrintings(partner.getGroupProductsOnPrintings());
    stockMove.setInvoicedPartner(declaration.getInvoicedPartner());
    if (stockMove.getPartner() != null) {
      setDefaultAutoMailSettings(stockMove);
    }
    return stockMove;
  }

  /**
   * Fill reservation date time in stock move lines with sale order following supplychain
   * configuration.
   *
   * @param stockMove
   * @param declaration
   */
  protected void setReservationDateTime(StockMove stockMove, Declaration declaration)
      throws AxelorException {
    SupplyChainConfig supplyChainConfig =
        supplyChainConfigService.getSupplyChainConfig(declaration.getCompany());

    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();
    if (stockMoveLineList == null) {
      stockMoveLineList = new ArrayList<>();
    }
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      LocalDateTime reservationDateTime;

      switch (supplyChainConfig.getDeclarationReservationDateSelect()) {
        case SupplyChainConfigRepository.SALE_ORDER_CONFIRMATION_DATE:
          reservationDateTime = declaration.getConfirmationDateTime();
          break;
        case SupplyChainConfigRepository.SALE_ORDER_SHIPPING_DATE:
          DeclarationLine declarationLine = stockMoveLine.getDeclarationLine();
          if (declarationLine == null || declarationLine.getEstimatedShippingDate() == null) {
            reservationDateTime = null;
          } else {
            reservationDateTime = declarationLine.getEstimatedShippingDate().atStartOfDay();
          }
          break;
        default:
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(
                  SupplychainExceptionMessage.RESERVATION_SALE_ORDER_DATE_CONFIG_INCORRECT_VALUE));
      }

      if (reservationDateTime == null) {
        reservationDateTime = appBaseService.getTodayDateTime().toLocalDateTime();
      }
      stockMoveLine.setReservationDateTime(reservationDateTime);
    }
  }

  /**
   * Set automatic mail configuration from the partner.
   *
   * @param stockMove
   */
  protected void setDefaultAutoMailSettings(StockMove stockMove) throws AxelorException {
    Partner partner = stockMove.getPartner();
    Company company = stockMove.getCompany();

    PartnerStockSettings mailSettings =
        partnerStockSettingsService.getOrCreateMailSettings(partner, company);

    stockMove.setRealStockMoveAutomaticMail(mailSettings.getRealStockMoveAutomaticMail());
    stockMove.setRealStockMoveMessageTemplate(mailSettings.getRealStockMoveMessageTemplate());
    stockMove.setPlannedStockMoveAutomaticMail(mailSettings.getPlannedStockMoveAutomaticMail());
    stockMove.setPlannedStockMoveMessageTemplate(mailSettings.getPlannedStockMoveMessageTemplate());
  }

  @Override
  public StockMoveLine createStockMoveLine(StockMove stockMove, DeclarationLine declarationLine)
      throws AxelorException {
    return createStockMoveLine(
        stockMove,
        declarationLine,
        declarationLineServiceSupplyChain.computeUndeliveredQty(declarationLine));
  }

  @Override
  public StockMoveLine createStockMoveLine(
      StockMove stockMove, DeclarationLine declarationLine, BigDecimal qty) throws AxelorException {

    if (this.isStockMoveProduct(declarationLine)) {

      Unit unit = declarationLine.getProduct().getUnit();
      BigDecimal priceDiscounted = declarationLine.getPriceDiscounted();
      BigDecimal requestedReservedQty =
          declarationLine.getRequestedReservedQty().subtract(declarationLine.getDeliveredQty());

      BigDecimal companyUnitPriceUntaxed =
          (BigDecimal)
              productCompanyService.get(
                  declarationLine.getProduct(),
                  "costPrice",
                  declarationLine.getDeclaration() != null
                      ? declarationLine.getDeclaration().getCompany()
                      : null);
      if (unit != null && !unit.equals(declarationLine.getUnit())) {
        qty =
            unitConversionService.convert(
                declarationLine.getUnit(), unit, qty, qty.scale(), declarationLine.getProduct());
        priceDiscounted =
            unitConversionService.convert(
                unit,
                declarationLine.getUnit(),
                priceDiscounted,
                appBaseService.getNbDecimalDigitForUnitPrice(),
                declarationLine.getProduct());
        requestedReservedQty =
            unitConversionService.convert(
                declarationLine.getUnit(),
                unit,
                requestedReservedQty,
                requestedReservedQty.scale(),
                declarationLine.getProduct());
      }

      BigDecimal taxRate = BigDecimal.ZERO;
      TaxLine taxLine = declarationLine.getTaxLine();
      if (taxLine != null) {
        taxRate = taxLine.getValue();
      }
      if (declarationLine.getQty().signum() != 0) {
        companyUnitPriceUntaxed =
            declarationLine
                .getCompanyExTaxTotal()
                .divide(
                    declarationLine.getQty(),
                    appBaseService.getNbDecimalDigitForUnitPrice(),
                    RoundingMode.HALF_UP);
      }

      StockMoveLine stockMoveLine =
          stockMoveLineSupplychainService.createStockMoveLine(
              declarationLine.getProduct(),
              declarationLine.getProductName(),
              declarationLine.getDescription(),
              qty,
              requestedReservedQty,
              priceDiscounted,
              companyUnitPriceUntaxed,
              null,
              unit,
              stockMove,
              StockMoveLineService.TYPE_SALES,
              declarationLine.getDeclaration().getInAti(),
              taxRate,
              declarationLine,
              null);

      if (declarationLine.getDeliveryState() == 0) {
        declarationLine.setDeliveryState(DeclarationLineRepository.DELIVERY_STATE_NOT_DELIVERED);
      }

      return stockMoveLine;
    }
    return null;
  }

  @Override
  public boolean isStockMoveProduct(DeclarationLine declarationLine) throws AxelorException {
    return isStockMoveProduct(declarationLine, declarationLine.getDeclaration());
  }

  @Override
  public boolean isStockMoveProduct(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException {

    Company company = declaration.getCompany();

    SupplyChainConfig supplyChainConfig = supplyChainConfigService.getSupplyChainConfig(company);

    Product product = declarationLine.getProduct();

    return (product != null
        && ((ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect())
                && supplyChainConfig.getHasOutSmForNonStorableProduct()
                && !product.getIsShippingCostsProduct())
            || (ProductRepository.PRODUCT_TYPE_STORABLE.equals(product.getProductTypeSelect())
                && supplyChainConfig.getHasOutSmForStorableProduct())));
  }

  protected boolean existActiveStockMoveForDeclarationLine(DeclarationLine declarationLine) {

    long stockMoveLineCount =
        stockMoveLineRepository
            .all()
            .filter(
                "self.declarationLine.id = ?1 AND self.stockMove.statusSelect in (?2,?3)",
                declarationLine.getId(),
                StockMoveRepository.STATUS_DRAFT,
                StockMoveRepository.STATUS_PLANNED)
            .count();

    return stockMoveLineCount > 0;
  }

  @Override
  public void updateDeliveryState(Declaration declaration) throws AxelorException {
    declaration.setDeliveryState(computeDeliveryState(declaration));
  }

  @Override
  public void fullyUpdateDeliveryState(Declaration declaration) throws AxelorException {
    declarationLineServiceSupplyChain.updateDeliveryStates(declaration.getDeclarationLineList());
    updateDeliveryState(declaration);
  }

  protected int computeDeliveryState(Declaration declaration) throws AxelorException {

    if (declaration.getDeclarationLineList() == null || declaration.getDeclarationLineList().isEmpty()) {
      return DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED;
    }

    int deliveryState = -1;

    for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {

      if (this.isStockMoveProduct(declarationLine, declaration)) {

        if (declarationLine.getDeliveryState() == DeclarationLineRepository.DELIVERY_STATE_DELIVERED) {
          if (deliveryState == DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED) {
            return DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED;
          } else {
            deliveryState = DeclarationRepository.DELIVERY_STATE_DELIVERED;
          }
        } else if (declarationLine.getDeliveryState()
            == DeclarationLineRepository.DELIVERY_STATE_NOT_DELIVERED) {
          if (deliveryState == DeclarationRepository.DELIVERY_STATE_DELIVERED) {
            return DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED;
          } else {
            deliveryState = DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED;
          }
        } else if (declarationLine.getDeliveryState()
            == DeclarationLineRepository.DELIVERY_STATE_PARTIALLY_DELIVERED) {
          return DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED;
        }
      }
    }
    return deliveryState;
  }

  @Override
  public Optional<Declaration> findDeclaration(StockMove stockMove) {
    if (StockMoveRepository.ORIGIN_SALE_ORDER.equals(stockMove.getOriginTypeSelect())
        && stockMove.getOriginId() != null) {
      return Optional.ofNullable(declarationRepository.find(stockMove.getOriginId()));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Use delivered partner if the configuration is set in generated stock move, else the default is
   * client partner.
   */
  protected Partner computePartnerToUseForStockMove(Declaration declaration) {
    if (appSupplychainService.getAppSupplychain().getActivatePartnerRelations()
        && declaration.getDeliveredPartner() != null) {
      return declaration.getDeliveredPartner();
    } else {
      return declaration.getClientPartner();
    }
  }
}
