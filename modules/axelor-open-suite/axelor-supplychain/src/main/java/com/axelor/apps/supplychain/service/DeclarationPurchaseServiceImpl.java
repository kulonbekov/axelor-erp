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

import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.service.PurchaseOrderService;
import com.axelor.apps.purchase.service.SupplierCatalogService;
import com.axelor.apps.purchase.service.config.PurchaseConfigService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.auth.AuthUtils;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationPurchaseServiceImpl implements DeclarationPurchaseService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected PurchaseOrderSupplychainService purchaseOrderSupplychainService;
  protected PurchaseOrderLineServiceSupplyChain purchaseOrderLineServiceSupplychain;
  protected PurchaseOrderService purchaseOrderService;
  protected PurchaseOrderRepository purchaseOrderRepository;
  protected PurchaseConfigService purchaseConfigService;
  protected AppBaseService appBaseService;
  protected PartnerPriceListService partnerPriceListService;
  protected SupplierCatalogService supplierCatalogService;

  @Inject
  public DeclarationPurchaseServiceImpl(
      PurchaseOrderSupplychainService purchaseOrderSupplychainService,
      PurchaseOrderLineServiceSupplyChain purchaseOrderLineServiceSupplychain,
      PurchaseOrderService purchaseOrderService,
      PurchaseOrderRepository purchaseOrderRepository,
      PurchaseConfigService purchaseConfigService,
      AppBaseService appBaseService,
      PartnerPriceListService partnerPriceListService,
      SupplierCatalogService supplierCatalogService) {
    this.purchaseOrderSupplychainService = purchaseOrderSupplychainService;
    this.purchaseOrderLineServiceSupplychain = purchaseOrderLineServiceSupplychain;
    this.purchaseOrderService = purchaseOrderService;
    this.purchaseOrderRepository = purchaseOrderRepository;
    this.purchaseConfigService = purchaseConfigService;
    this.appBaseService = appBaseService;
    this.partnerPriceListService = partnerPriceListService;
    this.supplierCatalogService = supplierCatalogService;
  }

  @Override
  public void createPurchaseOrders(Declaration declaration) throws AxelorException {

    Map<Partner, List<DeclarationLine>> declarationLinesBySupplierPartner =
        this.splitBySupplierPartner(declaration.getDeclarationLineList());

    for (Partner supplierPartner : declarationLinesBySupplierPartner.keySet()) {

      this.createPurchaseOrder(
          supplierPartner, declarationLinesBySupplierPartner.get(supplierPartner), declaration);
    }
  }

  @Override
  public Map<Partner, List<DeclarationLine>> splitBySupplierPartner(
      List<DeclarationLine> declarationLineList) throws AxelorException {

    Map<Partner, List<DeclarationLine>> declarationLinesBySupplierPartner = new HashMap<>();

    for (DeclarationLine declarationLine : declarationLineList) {

      if (declarationLine.getSaleSupplySelect() == ProductRepository.SALE_SUPPLY_PURCHASE) {

        Partner supplierPartner = declarationLine.getSupplierPartner();

        if (supplierPartner == null) {
          throw new AxelorException(
              declarationLine,
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(SupplychainExceptionMessage.SO_PURCHASE_1),
              declarationLine.getProductName());
        }

        if (!declarationLinesBySupplierPartner.containsKey(supplierPartner)) {
          declarationLinesBySupplierPartner.put(supplierPartner, new ArrayList<>());
        }

        declarationLinesBySupplierPartner.get(supplierPartner).add(declarationLine);
      }
    }

    return declarationLinesBySupplierPartner;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public PurchaseOrder createPurchaseOrder(
      Partner supplierPartner, List<DeclarationLine> declarationLineList, Declaration declaration)
      throws AxelorException {

    LOG.debug("Creation of a purchase order for the sale order : {}", declaration.getDeclarationSeq());

    PurchaseOrder purchaseOrder =
        createPurchaseOrderAndLines(supplierPartner, declarationLineList, declaration);
    getAndSetSupplierCatalogInfo(purchaseOrder);
    purchaseOrderRepository.save(purchaseOrder);

    return purchaseOrder;
  }

  protected PurchaseOrder createPurchaseOrderAndLines(
      Partner supplierPartner, List<DeclarationLine> declarationLineList, Declaration declaration)
      throws AxelorException {
    PurchaseOrder purchaseOrder = createPurchaseOrder(supplierPartner, declaration);
    createPurchaseOrderLines(declarationLineList, purchaseOrder);
    purchaseOrderService.computePurchaseOrder(purchaseOrder);
    return purchaseOrder;
  }

  protected PurchaseOrder createPurchaseOrder(Partner supplierPartner, Declaration declaration)
      throws AxelorException {
    PurchaseOrder purchaseOrder =
        purchaseOrderSupplychainService.createPurchaseOrder(
            AuthUtils.getUser(),
            declaration.getCompany(),
            supplierPartner.getContactPartnerSet().size() == 1
                ? supplierPartner.getContactPartnerSet().iterator().next()
                : null,
            supplierPartner.getCurrency(),
            null,
            declaration.getDeclarationSeq(),
            declaration.getExternalReference(),
            declaration.getDirectOrderLocation()
                ? declaration.getStockLocation()
                : purchaseOrderSupplychainService.getStockLocation(
                    supplierPartner, declaration.getCompany()),
            appBaseService.getTodayDate(declaration.getCompany()),
            partnerPriceListService.getDefaultPriceList(
                supplierPartner, PriceListRepository.TYPE_PURCHASE),
            supplierPartner,
            declaration.getTradingName());

    purchaseOrder.setGeneratedDeclarationId(declaration.getId());
    purchaseOrder.setGroupProductsOnPrintings(supplierPartner.getGroupProductsOnPrintings());

    Integer atiChoice =
        purchaseConfigService
            .getPurchaseConfig(declaration.getCompany())
            .getPurchaseOrderInAtiSelect();
    if (atiChoice == AccountConfigRepository.INVOICE_ATI_ALWAYS
        || atiChoice == AccountConfigRepository.INVOICE_ATI_DEFAULT) {
      purchaseOrder.setInAti(true);
    } else {
      purchaseOrder.setInAti(false);
    }

    purchaseOrder.setNotes(supplierPartner.getPurchaseOrderComments());
    return purchaseOrder;
  }

  protected void createPurchaseOrderLines(
      List<DeclarationLine> declarationLineList, PurchaseOrder purchaseOrder) throws AxelorException {
    Collections.sort(declarationLineList, Comparator.comparing(DeclarationLine::getSequence));
    for (DeclarationLine declarationLine : declarationLineList) {
      purchaseOrder.addPurchaseOrderLineListItem(
          purchaseOrderLineServiceSupplychain.createPurchaseOrderLine(
              purchaseOrder, declarationLine));
    }
  }

  protected void getAndSetSupplierCatalogInfo(PurchaseOrder purchaseOrder) throws AxelorException {
    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
      SupplierCatalog supplierCatalog =
          supplierCatalogService.getSupplierCatalog(
              purchaseOrderLine.getProduct(),
              purchaseOrder.getSupplierPartner(),
              purchaseOrder.getCompany());
      if (supplierCatalog != null) {
        purchaseOrderLine.setProductName(supplierCatalog.getProductSupplierName());
        purchaseOrderLine.setProductCode(supplierCatalog.getProductSupplierCode());
      }
    }
  }
}
