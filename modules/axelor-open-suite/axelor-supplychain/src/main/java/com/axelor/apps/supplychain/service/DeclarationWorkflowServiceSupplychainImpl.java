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
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.service.app.AppCrmService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.exception.BlockedDeclarationException;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.config.SaleConfigService;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.sale.service.declaration.DeclarationWorkflowServiceImpl;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.studio.db.AppSupplychain;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;

public class DeclarationWorkflowServiceSupplychainImpl extends DeclarationWorkflowServiceImpl {

  protected DeclarationStockService declarationStockService;
  protected DeclarationPurchaseService declarationPurchaseService;
  protected AppSupplychainService appSupplychainService;
  protected AccountingSituationSupplychainService accountingSituationSupplychainService;
  protected PartnerSupplychainService partnerSupplychainService;
  protected SaleConfigService saleConfigService;
  protected DeclarationCheckAnalyticService declarationCheckAnalyticService;

  @Inject
  public DeclarationWorkflowServiceSupplychainImpl(
      SequenceService sequenceService,
      PartnerRepository partnerRepo,
      DeclarationRepository declarationRepo,
      AppSaleService appSaleService,
      AppCrmService appCrmService,
      UserService userService,
      DeclarationLineService declarationLineService,
      DeclarationStockService declarationStockService,
      DeclarationPurchaseService declarationPurchaseService,
      AppSupplychainService appSupplychainService,
      AccountingSituationSupplychainService accountingSituationSupplychainService,
      PartnerSupplychainService partnerSupplychainService,
      SaleConfigService saleConfigService,
      DeclarationCheckAnalyticService declarationCheckAnalyticService) {
    super(
        sequenceService,
        partnerRepo,
        declarationRepo,
        appSaleService,
        appCrmService,
        userService,
        declarationLineService);
    this.declarationStockService = declarationStockService;
    this.declarationPurchaseService = declarationPurchaseService;
    this.appSupplychainService = appSupplychainService;
    this.accountingSituationSupplychainService = accountingSituationSupplychainService;
    this.partnerSupplychainService = partnerSupplychainService;
    this.saleConfigService = saleConfigService;
    this.declarationCheckAnalyticService = declarationCheckAnalyticService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirmDeclaration(Declaration declaration) throws AxelorException {

    if (!appSupplychainService.isApp("supplychain")) {
      super.confirmDeclaration(declaration);
      return;
    }

    if (saleConfigService
        .getSaleConfig(declaration.getCompany())
        .getIsAnalyticDistributionRequired()) {
      declarationCheckAnalyticService.checkDeclarationLinesAnalyticDistribution(declaration);
    }

    if (partnerSupplychainService.isBlockedPartnerOrParent(declaration.getClientPartner())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SupplychainExceptionMessage.CUSTOMER_HAS_BLOCKED_ACCOUNT));
    }

    super.confirmDeclaration(declaration);

    AppSupplychain appSupplychain = appSupplychainService.getAppSupplychain();

    if (appSupplychain.getPurchaseOrderGenerationAuto()) {
      declarationPurchaseService.createPurchaseOrders(declaration);
    }
    if (appSupplychain.getCustomerStockMoveGenerationAuto()) {
      declarationStockService.createStocksMovesFromDeclaration(declaration);
    }
    int intercoSaleCreatingStatus = appSupplychain.getIntercoSaleCreatingStatusSelect();
    if (declaration.getInterco()
        && intercoSaleCreatingStatus == DeclarationRepository.STATUS_ORDER_CONFIRMED) {
      Beans.get(IntercoService.class).generateIntercoPurchaseFromSale(declaration);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelDeclaration(
      Declaration declaration, CancelReason cancelReason, String cancelReasonStr)
      throws AxelorException {
    super.cancelDeclaration(declaration, cancelReason, cancelReasonStr);

    if (!appSupplychainService.isApp("supplychain")) {
      return;
    }
    try {
      accountingSituationSupplychainService.updateUsedCredit(declaration.getClientPartner());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  @Transactional(
      rollbackOn = {AxelorException.class, RuntimeException.class},
      ignore = {BlockedDeclarationException.class})
  public void finalizeQuotation(Declaration declaration) throws AxelorException {

    if (!appSupplychainService.isApp("supplychain")) {
      super.finalizeQuotation(declaration);
      return;
    }

    accountingSituationSupplychainService.updateCustomerCreditFromDeclaration(declaration);
    super.finalizeQuotation(declaration);
    int intercoSaleCreatingStatus =
        appSupplychainService.getAppSupplychain().getIntercoSaleCreatingStatusSelect();
    if (declaration.getInterco()
        && intercoSaleCreatingStatus == DeclarationRepository.STATUS_FINALIZED_QUOTATION) {
      Beans.get(IntercoService.class).generateIntercoPurchaseFromSale(declaration);
    }
    if (declaration.getCreatedByInterco()) {
      fillIntercompanyPurchaseOrderCounterpart(declaration);
    }
  }

  /**
   * Fill interco purchase order counterpart is the sale order exist.
   *
   * @param declaration
   */
  protected void fillIntercompanyPurchaseOrderCounterpart(Declaration declaration) {
    PurchaseOrder purchaseOrder =
        Beans.get(PurchaseOrderRepository.class)
            .all()
            .filter("self.purchaseOrderSeq = :purchaseOrderSeq")
            .bind("purchaseOrderSeq", declaration.getExternalReference())
            .fetchOne();
    if (purchaseOrder != null) {
      purchaseOrder.setExternalReference(declaration.getDeclarationSeq());
    }
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void completeDeclaration(Declaration declaration) throws AxelorException {

    if (!appSupplychainService.isApp("supplychain")) {
      super.completeDeclaration(declaration);
      return;
    }

    List<StockMove> stockMoves =
        Beans.get(StockMoveRepository.class)
            .all()
            .filter(
                "self.originId = ? AND self.originTypeSelect = ?",
                declaration.getId(),
                "com.axelor.apps.sale.db.Declaration")
            .fetch();
    if (!stockMoves.isEmpty()) {
      for (StockMove stockMove : stockMoves) {
        Integer statusSelect = stockMove.getStatusSelect();
        if (statusSelect == StockMoveRepository.STATUS_DRAFT
            || statusSelect == StockMoveRepository.STATUS_PLANNED) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(SupplychainExceptionMessage.SALE_ORDER_COMPLETE_MANUALLY));
        }
      }
    }
    super.completeDeclaration(declaration);
  }
}
