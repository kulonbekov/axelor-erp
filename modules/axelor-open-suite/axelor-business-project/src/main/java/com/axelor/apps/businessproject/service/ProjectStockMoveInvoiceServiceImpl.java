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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.axelor.apps.supplychain.service.StockMoveInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.StockMoveLineServiceSupplychain;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.Map;

public class ProjectStockMoveInvoiceServiceImpl extends StockMoveInvoiceServiceImpl {

  @Inject
  public ProjectStockMoveInvoiceServiceImpl(
      DeclarationInvoiceService declarationInvoiceService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      StockMoveLineServiceSupplychain stockMoveLineServiceSupplychain,
      InvoiceRepository invoiceRepository,
      DeclarationRepository declarationRepo,
      PurchaseOrderRepository purchaseOrderRepo,
      StockMoveLineRepository stockMoveLineRepository,
      InvoiceLineRepository invoiceLineRepository,
      SupplyChainConfigService supplyChainConfigService,
      AppSupplychainService appSupplychainService) {
    super(
        declarationInvoiceService,
        purchaseOrderInvoiceService,
        stockMoveLineServiceSupplychain,
        invoiceRepository,
        declarationRepo,
        purchaseOrderRepo,
        stockMoveLineRepository,
        invoiceLineRepository,
        supplyChainConfigService,
        appSupplychainService);
  }

  @Override
  public InvoiceLine createInvoiceLine(Invoice invoice, StockMoveLine stockMoveLine, BigDecimal qty)
      throws AxelorException {

    InvoiceLine invoiceLine = super.createInvoiceLine(invoice, stockMoveLine, qty);
    if (invoiceLine == null
        || !Beans.get(AppBusinessProjectService.class).isApp("business-project")) {
      return invoiceLine;
    }

    DeclarationLine declarationLine = invoiceLine.getDeclarationLine();
    if (declarationLine != null) {
      invoiceLine.setProject(declarationLine.getProject());
    }

    PurchaseOrderLine purchaseOrderLine = invoiceLine.getPurchaseOrderLine();
    if (purchaseOrderLine != null) {
      invoiceLine.setProject(purchaseOrderLine.getProject());
    }

    return invoiceLine;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice createInvoiceFromDeclaration(
      StockMove stockMove, Declaration declaration, Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {
    Invoice invoice = super.createInvoiceFromDeclaration(stockMove, declaration, qtyToInvoiceMap);

    if (invoice != null) {
      Project project = declaration.getProject();
      if (project != null) {
        invoice.setProject(project);
      }
      invoiceRepository.save(invoice);
    }
    return invoice;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice createInvoiceFromPurchaseOrder(
      StockMove stockMove, PurchaseOrder purchaseOrder, Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {
    Invoice invoice =
        super.createInvoiceFromPurchaseOrder(stockMove, purchaseOrder, qtyToInvoiceMap);
    if (invoice != null) {
      Project project = purchaseOrder.getProject();
      if (project != null) {
        invoice.setProject(project);
      }
      invoiceRepository.save(invoice);
    }
    return invoice;
  }
}
