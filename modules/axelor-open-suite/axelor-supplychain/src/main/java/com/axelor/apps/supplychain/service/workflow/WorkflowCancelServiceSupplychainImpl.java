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
package com.axelor.apps.supplychain.service.workflow;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.invoice.workflow.cancel.WorkflowCancelServiceImpl;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowCancelServiceSupplychainImpl extends WorkflowCancelServiceImpl {

  private int oldInvoiceStatusSelect;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private DeclarationInvoiceService declarationInvoiceService;

  private PurchaseOrderInvoiceService purchaseOrderInvoiceService;

  private DeclarationRepository declarationRepository;

  @Inject
  public WorkflowCancelServiceSupplychainImpl(
      DeclarationInvoiceService declarationInvoiceService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      DeclarationRepository declarationRepository,
      PurchaseOrderRepository purchaseOrderRepository) {

    this.declarationInvoiceService = declarationInvoiceService;
    this.purchaseOrderInvoiceService = purchaseOrderInvoiceService;
    this.declarationRepository = declarationRepository;
    this.purchaseOrderRepository = purchaseOrderRepository;
  }

  private PurchaseOrderRepository purchaseOrderRepository;

  @Override
  public void beforeCancel(Invoice invoice) {
    this.oldInvoiceStatusSelect = invoice.getStatusSelect();
  }

  @Override
  public void afterCancel(Invoice invoice) throws AxelorException {
    if (oldInvoiceStatusSelect == InvoiceRepository.STATUS_VENTILATED) {

      if (InvoiceToolService.isPurchase(invoice)) {

        // Update amount invoiced on PurchaseOrder
        this.purchaseOrderProcess(invoice);

      } else {

        // Update amount remaining to invoiced on Declaration
        this.declarationProcess(invoice);
      }
    }
  }

  public void declarationProcess(Invoice invoice) throws AxelorException {

    Declaration invoiceDeclaration = invoice.getDeclaration();

    if (invoiceDeclaration != null) {

      log.debug(
          "Update the invoiced amount of the sale order : {}", invoiceDeclaration.getDeclarationSeq());
      invoiceDeclaration.setAmountInvoiced(
          declarationInvoiceService.getInvoicedAmount(invoiceDeclaration, invoice.getId(), true));

    } else {

      // Get all different declarations from invoice
      List<Declaration> declarationList = Lists.newArrayList();

      for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {

        Declaration declaration = this.declarationLineProcess(invoice, invoiceLine);

        if (declaration != null && !declarationList.contains(declaration)) {
          declarationList.add(declaration);
        }
      }

      for (Declaration declaration : declarationList) {
        log.debug("Update the invoiced amount of the sale order : {}", declaration.getDeclarationSeq());
        declaration.setAmountInvoiced(
            declarationInvoiceService.getInvoicedAmount(declaration, invoice.getId(), true));
        declarationRepository.save(declaration);
      }
    }
  }

  public Declaration declarationLineProcess(Invoice invoice, InvoiceLine invoiceLine)
      throws AxelorException {

    DeclarationLine declarationLine = invoiceLine.getDeclarationLine();

    if (declarationLine == null) {
      return null;
    }

    Declaration declaration = declarationLine.getDeclaration();

    // Update invoiced amount on sale order line
    BigDecimal invoicedAmountToAdd = invoiceLine.getExTaxTotal();

    // If is it a refund invoice, so we negate the amount invoiced
    if (InvoiceToolService.isRefund(invoiceLine.getInvoice())) {
      invoicedAmountToAdd = invoicedAmountToAdd.negate();
    }

    if (!invoice.getCurrency().equals(declaration.getCurrency())
        && declarationLine.getCompanyExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {
      // If the sale order currency is different from the invoice currency, use company currency to
      // calculate a rate. This rate will be applied to sale order line
      BigDecimal currentCompanyInvoicedAmount = invoiceLine.getCompanyExTaxTotal();
      BigDecimal rate =
          currentCompanyInvoicedAmount.divide(
              declarationLine.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
      invoicedAmountToAdd = rate.multiply(declarationLine.getExTaxTotal());
    }

    declarationLine.setAmountInvoiced(
        declarationLine.getAmountInvoiced().subtract(invoicedAmountToAdd));

    return declaration;
  }

  public void purchaseOrderProcess(Invoice invoice) throws AxelorException {

    PurchaseOrder invoicePurchaseOrder = invoice.getPurchaseOrder();

    if (invoicePurchaseOrder != null) {

      log.debug(
          "Update the invoiced amount of the purchase order : {}",
          invoicePurchaseOrder.getPurchaseOrderSeq());
      invoicePurchaseOrder.setAmountInvoiced(
          purchaseOrderInvoiceService.getInvoicedAmount(
              invoicePurchaseOrder, invoice.getId(), true));

    } else {

      // Get all different purchaseOrders from invoice

      List<PurchaseOrder> purchaseOrderList = Lists.newArrayList();

      for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {

        PurchaseOrder purchaseOrder = this.purchaseOrderLineProcess(invoice, invoiceLine);

        if (purchaseOrder != null && !purchaseOrderList.contains(purchaseOrder)) {
          purchaseOrderList.add(purchaseOrder);
        }
      }

      for (PurchaseOrder purchaseOrder : purchaseOrderList) {
        log.debug(
            "Update the invoiced amount of the purchase order : {}",
            purchaseOrder.getPurchaseOrderSeq());
        purchaseOrder.setAmountInvoiced(
            purchaseOrderInvoiceService.getInvoicedAmount(purchaseOrder, invoice.getId(), true));
        purchaseOrderRepository.save(purchaseOrder);
      }
    }
  }

  public PurchaseOrder purchaseOrderLineProcess(Invoice invoice, InvoiceLine invoiceLine)
      throws AxelorException {

    PurchaseOrderLine purchaseOrderLine = invoiceLine.getPurchaseOrderLine();

    if (purchaseOrderLine == null) {
      return null;
    }

    PurchaseOrder purchaseOrder = purchaseOrderLine.getPurchaseOrder();

    BigDecimal invoicedAmountToAdd = invoiceLine.getExTaxTotal();

    // If is it a refund invoice, so we negate the amount invoiced
    if (InvoiceToolService.isRefund(invoiceLine.getInvoice())) {
      invoicedAmountToAdd = invoicedAmountToAdd.negate();
    }

    // Update invoiced amount on purchase order line
    if (!invoice.getCurrency().equals(purchaseOrder.getCurrency())
        && purchaseOrderLine.getCompanyExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {
      // If the purchase order currency is different from the invoice currency, use company currency
      // to calculate a rate. This rate will be applied to purchase order line
      BigDecimal currentCompanyInvoicedAmount = invoiceLine.getCompanyExTaxTotal();
      BigDecimal rate =
          currentCompanyInvoicedAmount.divide(
              purchaseOrderLine.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
      invoicedAmountToAdd = rate.multiply(purchaseOrderLine.getExTaxTotal());
    }

    purchaseOrderLine.setAmountInvoiced(
        purchaseOrderLine.getAmountInvoiced().subtract(invoicedAmountToAdd));

    return purchaseOrder;
  }
}
