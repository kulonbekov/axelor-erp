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

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.analytic.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductMultipleQtyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.base.service.pricing.PricingService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.purchase.service.app.AppPurchaseService;
import com.axelor.apps.sale.db.PackLine;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationLineServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationMarginService;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.db.repo.SupplyChainConfigRepository;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.inject.Beans;
import com.axelor.utils.StringTool;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.persistence.TypedQuery;

public class DeclarationLineServiceSupplyChainImpl extends DeclarationLineServiceImpl
    implements DeclarationLineServiceSupplyChain {

  protected AppAccountService appAccountService;
  protected AnalyticMoveLineService analyticMoveLineService;
  protected AppSupplychainService appSupplychainService;
  protected AccountConfigService accountConfigService;
  protected InvoiceLineRepository invoiceLineRepository;
  protected SaleInvoicingStateService saleInvoicingStateService;

  @Inject
  public DeclarationLineServiceSupplyChainImpl(
      CurrencyService currencyService,
      PriceListService priceListService,
      ProductMultipleQtyService productMultipleQtyService,
      AppBaseService appBaseService,
      AppSaleService appSaleService,
      AccountManagementService accountManagementService,
      DeclarationLineRepository declarationLineRepo,
      DeclarationService declarationService,
      AppAccountService appAccountService,
      AnalyticMoveLineService analyticMoveLineService,
      AppSupplychainService appSupplychainService,
      AccountConfigService accountConfigService,
      PricingService pricingService,
      TaxService taxService,
      DeclarationMarginService declarationMarginService,
      InvoiceLineRepository invoiceLineRepository,
      SaleInvoicingStateService saleInvoicingStateService) {
    super(
        currencyService,
        priceListService,
        productMultipleQtyService,
        appBaseService,
        appSaleService,
        accountManagementService,
        declarationLineRepo,
        declarationService,
        pricingService,
        taxService,
        declarationMarginService);
    this.appAccountService = appAccountService;
    this.analyticMoveLineService = analyticMoveLineService;
    this.appSupplychainService = appSupplychainService;
    this.accountConfigService = accountConfigService;
    this.invoiceLineRepository = invoiceLineRepository;
    this.saleInvoicingStateService = saleInvoicingStateService;
  }

  @Override
  public void computeProductInformation(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException {
    super.computeProductInformation(declarationLine, declaration);
    declarationLine.setSaleSupplySelect(declarationLine.getProduct().getSaleSupplySelect());

    if (appAccountService.isApp("supplychain")) {
      declarationLine.setSaleSupplySelect(declarationLine.getProduct().getSaleSupplySelect());

      this.getAndComputeAnalyticDistribution(declarationLine, declaration);
    }
  }

  public DeclarationLine getAndComputeAnalyticDistribution(
      DeclarationLine declarationLine, Declaration declaration) throws AxelorException {

    AccountConfig accountConfig = accountConfigService.getAccountConfig(declaration.getCompany());

    if (!accountConfig.getManageAnalyticAccounting()
        || accountConfig.getAnalyticDistributionTypeSelect()
            == AccountConfigRepository.DISTRIBUTION_TYPE_FREE) {
      return declarationLine;
    }

    AnalyticDistributionTemplate analyticDistributionTemplate =
        analyticMoveLineService.getAnalyticDistributionTemplate(
            declaration.getClientPartner(),
            declarationLine.getProduct(),
            declaration.getCompany(),
            false);

    declarationLine.setAnalyticDistributionTemplate(analyticDistributionTemplate);

    if (declarationLine.getAnalyticMoveLineList() != null) {
      declarationLine.getAnalyticMoveLineList().clear();
    }

    this.computeAnalyticDistribution(declarationLine);

    return declarationLine;
  }

  @Override
  public DeclarationLine computeAnalyticDistribution(DeclarationLine declarationLine) {

    List<AnalyticMoveLine> analyticMoveLineList = declarationLine.getAnalyticMoveLineList();

    if ((analyticMoveLineList == null || analyticMoveLineList.isEmpty())) {
      createAnalyticDistributionWithTemplate(declarationLine);
    }
    if (analyticMoveLineList != null) {
      LocalDate date =
          appAccountService.getTodayDate(
              declarationLine.getDeclaration() != null
                  ? declarationLine.getDeclaration().getCompany()
                  : Optional.ofNullable(AuthUtils.getUser())
                      .map(User::getActiveCompany)
                      .orElse(null));
      for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
        analyticMoveLineService.updateAnalyticMoveLine(
            analyticMoveLine, declarationLine.getCompanyExTaxTotal(), date);
      }
    }
    return declarationLine;
  }

  public DeclarationLine createAnalyticDistributionWithTemplate(DeclarationLine declarationLine) {
    List<AnalyticMoveLine> analyticMoveLineList =
        analyticMoveLineService.generateLines(
            declarationLine.getAnalyticDistributionTemplate(),
            declarationLine.getCompanyExTaxTotal(),
            AnalyticMoveLineRepository.STATUS_FORECAST_ORDER,
            appAccountService.getTodayDate(
                declarationLine.getDeclaration() != null
                    ? declarationLine.getDeclaration().getCompany()
                    : Optional.ofNullable(AuthUtils.getUser())
                        .map(User::getActiveCompany)
                        .orElse(null)));

    if (ObjectUtils.isEmpty(analyticMoveLineList)) {
      declarationLine.clearAnalyticMoveLineList();
    } else {
      declarationLine.setAnalyticMoveLineList(analyticMoveLineList);
    }
    return declarationLine;
  }

  @Override
  public int getDeclarationLineInvoicingState(DeclarationLine declarationLine) {
    return saleInvoicingStateService.getInvoicingState(
        declarationLine.getAmountInvoiced(),
        declarationLine.getExTaxTotal(),
        atLeastOneInvoiceVentilated(declarationLine));
  }

  protected boolean atLeastOneInvoiceVentilated(DeclarationLine declarationLine) {
    return invoiceLineRepository
            .all()
            .filter(
                "self.declarationLine = :declarationLine AND self.invoice.statusSelect = :statusSelect")
            .bind("declarationLine", declarationLine.getId())
            .bind("statusSelect", InvoiceRepository.STATUS_VENTILATED)
            .count()
        > 0;
  }

  @Override
  public BigDecimal getAvailableStock(Declaration declaration, DeclarationLine declarationLine) {

    if (!appAccountService.isApp("supplychain")) {
      return super.getAvailableStock(declaration, declarationLine);
    }

    StockLocationLine stockLocationLine =
        Beans.get(StockLocationLineService.class)
            .getStockLocationLine(declaration.getStockLocation(), declarationLine.getProduct());

    if (stockLocationLine == null) {
      return BigDecimal.ZERO;
    }
    return stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
  }

  @Override
  public BigDecimal getAllocatedStock(Declaration declaration, DeclarationLine declarationLine) {

    if (!appAccountService.isApp("supplychain")) {
      return super.getAllocatedStock(declaration, declarationLine);
    }

    StockLocationLine stockLocationLine =
        Beans.get(StockLocationLineService.class)
            .getStockLocationLine(declaration.getStockLocation(), declarationLine.getProduct());

    if (stockLocationLine == null) {
      return BigDecimal.ZERO;
    }
    return stockLocationLine.getReservedQty();
  }

  @Override
  public BigDecimal computeUndeliveredQty(DeclarationLine declarationLine) {
    Preconditions.checkNotNull(declarationLine);

    BigDecimal undeliveryQty = declarationLine.getQty().subtract(declarationLine.getDeliveredQty());

    if (undeliveryQty.signum() > 0) {
      return undeliveryQty;
    }
    return BigDecimal.ZERO;
  }

  @Override
  public List<Long> getSupplierPartnerList(DeclarationLine declarationLine) {
    Product product = declarationLine.getProduct();
    if (!Beans.get(AppPurchaseService.class).getAppPurchase().getManageSupplierCatalog()
        || product == null
        || product.getSupplierCatalogList() == null) {
      return new ArrayList<>();
    }
    return product.getSupplierCatalogList().stream()
        .map(SupplierCatalog::getSupplierPartner)
        .filter(Objects::nonNull)
        .map(Partner::getId)
        .collect(Collectors.toList());
  }

  @Override
  public void updateDeliveryStates(List<DeclarationLine> declarationLineList) {
    if (ObjectUtils.isEmpty(declarationLineList)) {
      return;
    }

    for (DeclarationLine declarationLine : declarationLineList) {
      updateDeliveryState(declarationLine);
    }
  }

  @Override
  public void updateDeliveryState(DeclarationLine declarationLine) {
    if (declarationLine.getDeliveredQty().signum() == 0) {
      declarationLine.setDeliveryState(DeclarationLineRepository.DELIVERY_STATE_NOT_DELIVERED);
    } else if (declarationLine.getDeliveredQty().compareTo(declarationLine.getQty()) < 0) {
      declarationLine.setDeliveryState(DeclarationLineRepository.DELIVERY_STATE_PARTIALLY_DELIVERED);
    } else {
      declarationLine.setDeliveryState(DeclarationLineRepository.DELIVERY_STATE_DELIVERED);
    }
  }

  @Override
  public String getDeclarationLineListForAProduct(
      Long productId, Long companyId, Long stockLocationId) {
    List<Integer> statusList = new ArrayList<>();
    statusList.add(DeclarationRepository.STATUS_ORDER_CONFIRMED);
    String status =
        appSupplychainService.getAppSupplychain().getsOFilterOnStockDetailStatusSelect();
    if (!StringUtils.isBlank(status)) {
      statusList = StringTool.getIntegerList(status);
    }
    String statusListQuery =
        statusList.stream().map(String::valueOf).collect(Collectors.joining(","));
    String query =
        "self.product.id = "
            + productId
            + " AND self.deliveryState != "
            + DeclarationLineRepository.DELIVERY_STATE_DELIVERED
            + " AND self.declaration.statusSelect IN ("
            + statusListQuery
            + ")";

    if (companyId != 0L) {
      query += " AND self.declaration.company.id = " + companyId;
      if (stockLocationId != 0L) {
        StockLocation stockLocation =
            Beans.get(StockLocationRepository.class).find(stockLocationId);
        List<StockLocation> stockLocationList =
            Beans.get(StockLocationService.class)
                .getAllLocationAndSubLocation(stockLocation, false);
        if (!stockLocationList.isEmpty() && stockLocation.getCompany().getId().equals(companyId)) {
          query +=
              " AND self.declaration.stockLocation.id IN ("
                  + StringTool.getIdListString(stockLocationList)
                  + ") ";
        }
      }
    }
    return query;
  }

  @Override
  public BigDecimal checkInvoicedOrDeliveredOrderQty(DeclarationLine declarationLine) {
    BigDecimal qty = declarationLine.getQty();
    BigDecimal deliveredQty = declarationLine.getDeliveredQty();
    BigDecimal invoicedQty = getInvoicedQty(declarationLine);

    if (qty.compareTo(invoicedQty) < 0 && invoicedQty.compareTo(deliveredQty) > 0) {
      return invoicedQty;
    } else if (deliveredQty.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(deliveredQty) < 0) {
      return deliveredQty;
    }

    return qty;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void updateStockMoveReservationDateTime(DeclarationLine declarationLine)
      throws AxelorException {
    Declaration declaration = declarationLine.getDeclaration();
    if (declaration == null) {
      return;
    }
    if (SupplyChainConfigRepository.SALE_ORDER_SHIPPING_DATE
        != Beans.get(SupplyChainConfigService.class)
            .getSupplyChainConfig(declaration.getCompany())
            .getDeclarationReservationDateSelect()) {
      return;
    }

    Beans.get(StockMoveLineRepository.class)
        .all()
        .filter("self.declarationLine = :declarationLineId")
        .bind("declarationLineId", declarationLine.getId())
        .fetchStream()
        .filter(
            stockMoveLine ->
                stockMoveLine.getStockMove() != null
                    && stockMoveLine.getStockMove().getStatusSelect()
                        == StockMoveRepository.STATUS_PLANNED)
        .forEach(
            stockMoveLine ->
                stockMoveLine.setReservationDateTime(
                    declarationLine.getEstimatedShippingDate().atStartOfDay()));
  }

  @Override
  public DeclarationLine updateProductQty(
      DeclarationLine declarationLine, Declaration declaration, BigDecimal oldQty, BigDecimal newQty)
      throws AxelorException {
    BigDecimal qty = declarationLine.getQty();
    qty =
        qty.divide(oldQty, appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_EVEN)
            .multiply(newQty)
            .setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_EVEN);
    declarationLine.setQty(qty);

    if (appSupplychainService.isApp("supplychain")
        && declaration.getStatusSelect() == DeclarationRepository.STATUS_ORDER_CONFIRMED) {
      qty = this.checkInvoicedOrDeliveredOrderQty(declarationLine);
      declarationLine.setQty(qty);
    }

    declarationLine = super.updateProductQty(declarationLine, declaration, oldQty, newQty);
    if (!appSupplychainService.isApp("supplychain")
        || declarationLine.getTypeSelect() != DeclarationLineRepository.TYPE_NORMAL) {
      return declarationLine;
    }
    if (appAccountService.getAppAccount().getManageAnalyticAccounting()) {
      this.computeAnalyticDistribution(declarationLine);
    }
    if (appSupplychainService.getAppSupplychain().getManageStockReservation()
        && (declarationLine.getRequestedReservedQty().compareTo(qty) > 0
            || declarationLine.getIsQtyRequested())) {
      declarationLine.setRequestedReservedQty(BigDecimal.ZERO.max(qty));
    }
    return declarationLine;
  }

  @Override
  public DeclarationLine createDeclarationLine(
      PackLine packLine,
      Declaration declaration,
      BigDecimal packQty,
      BigDecimal conversionRate,
      Integer sequence)
      throws AxelorException {

    DeclarationLine soLine =
        super.createDeclarationLine(packLine, declaration, packQty, conversionRate, sequence);

    if (soLine != null && soLine.getProduct() != null) {
      soLine.setSaleSupplySelect(soLine.getProduct().getSaleSupplySelect());
      getAndComputeAnalyticDistribution(soLine, declaration);
      if (ObjectUtils.notEmpty(soLine.getAnalyticMoveLineList())) {
        soLine.getAnalyticMoveLineList().stream()
            .forEach(analyticMoveLine -> analyticMoveLine.setDeclarationLine(soLine));
      }
      try {
        SupplyChainConfig supplyChainConfig =
            Beans.get(SupplyChainConfigService.class).getSupplyChainConfig(declaration.getCompany());
        if (supplyChainConfig.getAutoRequestReservedQty()) {
          Beans.get(ReservedQtyService.class).requestQty(soLine);
        }
      } catch (AxelorException e) {
        TraceBackService.trace(e);
      }
    }
    return soLine;
  }

  protected BigDecimal getInvoicedQty(DeclarationLine declarationLine) {

    TypedQuery<BigDecimal> query =
        JPA.em()
            .createQuery(
                "SELECT COALESCE(SUM(CASE WHEN self.invoice.operationTypeSelect = 3 THEN self.qty WHEN self.invoice.operationTypeSelect = 4 THEN -self.qty END),0) FROM InvoiceLine self WHERE self.invoice.statusSelect = :statusSelect AND self.declarationLine.id = :declarationLineId",
                BigDecimal.class);
    query.setParameter("statusSelect", InvoiceRepository.STATUS_VENTILATED);
    query.setParameter("declarationLineId", declarationLine.getId());

    return query.getSingleResult();
  }
}
