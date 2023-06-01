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
package com.axelor.csv.script;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.script.ImportPurchaseOrder;
import com.axelor.apps.purchase.service.PurchaseOrderService;
import com.axelor.apps.purchase.service.PurchaseOrderWorkflowService;
import com.axelor.apps.sale.db.SaleConfig;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.SaleConfigRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.declaration.DeclarationWorkflowService;
import com.axelor.apps.stock.db.Inventory;
import com.axelor.apps.stock.db.InventoryLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.InventoryLineService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.PurchaseOrderStockServiceImpl;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.axelor.apps.supplychain.service.DeclarationStockService;
import com.axelor.apps.supplychain.service.SupplychainSaleConfigService;
import com.axelor.auth.AuthUtils;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ImportSupplyChain {

  @Inject protected PurchaseOrderService purchaseOrderService;

  @Inject protected PurchaseOrderWorkflowService purchaseOrderWorkflowService;

  @Inject protected PurchaseOrderStockServiceImpl purchaseOrderStockServiceImpl;

  @Inject protected InvoiceService invoiceService;

  @Inject protected DeclarationStockService declarationStockService;

  @Inject protected StockMoveRepository stockMoveRepo;

  @Inject protected DeclarationRepository declarationRepo;

  @Inject protected SaleConfigRepository saleConfigRepo;

  @Inject protected SupplychainSaleConfigService configService;

  @Inject protected StockConfigService stockConfigService;

  @Inject protected ImportPurchaseOrder importPurchaseOrder;

  @Inject protected ImportDeclaration importDeclaration;

  @Inject protected InventoryLineService inventoryLineService;

  @Inject protected InvoiceTermService invoiceTermService;

  @SuppressWarnings("rawtypes")
  public Object importSupplyChain(Object bean, Map values) {

    List<SaleConfig> configs = saleConfigRepo.all().fetch();
    for (SaleConfig config : configs) {
      configService.updateCustomerCredit(config);
    }

    return bean;
  }

  @Transactional(rollbackOn = {Exception.class})
  public Object importPurchaseOrderFromSupplyChain(Object bean, Map<String, Object> values) {

    try {
      StockMoveService stockMoveService = Beans.get(StockMoveService.class);

      PurchaseOrder purchaseOrder = (PurchaseOrder) bean;
      int status = purchaseOrder.getStatusSelect();
      purchaseOrder = (PurchaseOrder) importPurchaseOrder.importPurchaseOrder(bean, values);
      for (PurchaseOrderLine line : purchaseOrder.getPurchaseOrderLineList()) {
        Product product = line.getProduct();
        if (product.getMassUnit() == null) {
          product.setMassUnit(
              stockConfigService.getStockConfig(purchaseOrder.getCompany()).getCustomsMassUnit());
        }
      }

      if (status == PurchaseOrderRepository.STATUS_VALIDATED
          || status == PurchaseOrderRepository.STATUS_FINISHED) {
        purchaseOrderWorkflowService.validatePurchaseOrder(purchaseOrder);
      }

      if (status == PurchaseOrderRepository.STATUS_FINISHED) {
        List<Long> idList =
            purchaseOrderStockServiceImpl.createStockMoveFromPurchaseOrder(purchaseOrder);
        for (Long id : idList) {
          StockMove stockMove = stockMoveRepo.find(id);
          stockMoveService.copyQtyToRealQty(stockMove);
          stockMoveService.realize(stockMove);
          stockMove.setRealDate(purchaseOrder.getEstimatedReceiptDate());
        }
        purchaseOrder.setValidationDateTime(purchaseOrder.getOrderDate().atStartOfDay());
        purchaseOrder.setValidatedByUser(AuthUtils.getUser());
        purchaseOrder.setSupplierPartner(purchaseOrderService.validateSupplier(purchaseOrder));
        Invoice invoice =
            Beans.get(PurchaseOrderInvoiceService.class).generateInvoice(purchaseOrder);

        String prefixSupplierSeq = "INV000";
        invoice.setSupplierInvoiceNb(prefixSupplierSeq + purchaseOrder.getImportId());

        invoice.setInternalReference(purchaseOrder.getInternalReference());

        LocalDateTime dateTime;
        if (purchaseOrder.getValidationDateTime() != null) {
          dateTime = purchaseOrder.getValidationDateTime();
        } else {
          dateTime =
              Beans.get(AppBaseService.class)
                  .getTodayDateTime(purchaseOrder.getCompany())
                  .toLocalDateTime();
        }
        invoice.setInvoiceDate(dateTime.toLocalDate());
        invoice.setOriginDate(dateTime.toLocalDate().minusDays(15));
        invoiceTermService.computeInvoiceTerms(invoice);

        invoiceService.validateAndVentilate(invoice);
        if (purchaseOrder.getStatusSelect() != PurchaseOrderRepository.STATUS_FINISHED) {
          purchaseOrderWorkflowService.finishPurchaseOrder(purchaseOrder);
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return null;
  }

  @Transactional(rollbackOn = {Exception.class})
  public Object importDeclarationFromSupplyChain(Object bean, Map<String, Object> values) {
    try {
      DeclarationWorkflowService declarationWorkflowService = Beans.get(DeclarationWorkflowService.class);
      StockMoveService stockMoveService = Beans.get(StockMoveService.class);

      Declaration declaration = (Declaration) importDeclaration.importDeclaration(bean, values);

      for (DeclarationLine line : declaration.getDeclarationLineList()) {
        Product product = line.getProduct();
        if (product.getMassUnit() == null) {
          product.setMassUnit(
              stockConfigService.getStockConfig(declaration.getCompany()).getCustomsMassUnit());
        }
      }
      if (declaration.getStatusSelect() == DeclarationRepository.STATUS_FINALIZED_QUOTATION) {
        // taskDeclarationService.createTasks(declaration); TODO once we will have done the generation//
        // of tasks in project module
        declarationWorkflowService.confirmDeclaration(declaration);
        // Beans.get(DeclarationPurchaseService.class).createPurchaseOrders(declaration);
        //				productionOrderDeclarationService.generateProductionOrder(declaration);
        // declaration.setClientPartner(declarationWorkflowService.validateCustomer(declaration));
        // Generate invoice from sale order
        Invoice invoice = Beans.get(DeclarationInvoiceService.class).generateInvoice(declaration);
        if (declaration.getConfirmationDateTime() != null) {
          invoice.setInvoiceDate(declaration.getConfirmationDateTime().toLocalDate());

        } else {
          invoice.setInvoiceDate(
              Beans.get(AppBaseService.class).getTodayDate(declaration.getCompany()));
        }
        invoiceTermService.computeInvoiceTerms(invoice);
        invoiceService.validateAndVentilate(invoice);

        List<Long> idList = declarationStockService.createStocksMovesFromDeclaration(declaration);
        for (Long id : idList) {
          StockMove stockMove = stockMoveRepo.find(id);
          if (stockMove.getStockMoveLineList() != null
              && !stockMove.getStockMoveLineList().isEmpty()) {
            stockMoveService.copyQtyToRealQty(stockMove);
            stockMoveService.realize(stockMove);
            if (declaration.getConfirmationDateTime() != null) {
              stockMove.setRealDate(declaration.getConfirmationDateTime().toLocalDate());
            }
          }
        }
      }
      declarationRepo.save(declaration);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return null;
  }

  public Object importInventory(Object bean, Map<String, Object> values) throws AxelorException {

    assert bean instanceof Inventory;

    Inventory inventory = (Inventory) bean;

    for (InventoryLine inventoryLine : inventory.getInventoryLineList()) {
      inventoryLineService.compute(inventoryLine, inventoryLine.getInventory());
    }

    return inventory;
  }
}
