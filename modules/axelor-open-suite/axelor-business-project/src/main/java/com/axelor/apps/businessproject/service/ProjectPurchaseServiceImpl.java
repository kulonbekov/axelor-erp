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
package com.axelor.apps.businessproject.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.service.PurchaseOrderService;
import com.axelor.apps.purchase.service.SupplierCatalogService;
import com.axelor.apps.purchase.service.config.PurchaseConfigService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.supplychain.service.PurchaseOrderLineServiceSupplyChain;
import com.axelor.apps.supplychain.service.PurchaseOrderSupplychainService;
import com.axelor.apps.supplychain.service.DeclarationPurchaseServiceImpl;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;

public class ProjectPurchaseServiceImpl extends DeclarationPurchaseServiceImpl {

  @Inject
  public ProjectPurchaseServiceImpl(
      PurchaseOrderSupplychainService purchaseOrderSupplychainService,
      PurchaseOrderLineServiceSupplyChain purchaseOrderLineServiceSupplychain,
      PurchaseOrderService purchaseOrderService,
      PurchaseOrderRepository purchaseOrderRepository,
      PurchaseConfigService purchaseConfigService,
      AppBaseService appBaseService,
      PartnerPriceListService partnerPriceListService,
      SupplierCatalogService supplierCatalogService) {
    super(
        purchaseOrderSupplychainService,
        purchaseOrderLineServiceSupplychain,
        purchaseOrderService,
        purchaseOrderRepository,
        purchaseConfigService,
        appBaseService,
        partnerPriceListService,
        supplierCatalogService);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public PurchaseOrder createPurchaseOrder(
      Partner supplierPartner, List<DeclarationLine> declarationLineList, Declaration declaration)
      throws AxelorException {
    PurchaseOrder purchaseOrder =
        super.createPurchaseOrder(supplierPartner, declarationLineList, declaration);

    if (purchaseOrder != null
        && declaration != null
        && Beans.get(AppBusinessProjectService.class).isApp("business-project")) {
      purchaseOrder.setProject(declaration.getProject());
    }

    return purchaseOrder;
  }
}
