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

import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.sale.service.declaration.DeclarationWorkflowService;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.service.CommonInvoiceService;
import com.axelor.apps.supplychain.service.SaleInvoicingStateService;
import com.axelor.apps.supplychain.service.DeclarationInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.invoice.InvoiceServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineOrderService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class DeclarationInvoiceProjectServiceImpl extends DeclarationInvoiceServiceImpl {

  protected AppBusinessProjectService appBusinessProjectService;

  @Inject
  public DeclarationInvoiceProjectServiceImpl(
      AppBaseService appBaseService,
      AppSupplychainService appSupplychainService,
      DeclarationRepository declarationRepo,
      InvoiceRepository invoiceRepo,
      InvoiceServiceSupplychainImpl invoiceService,
      DeclarationLineService declarationLineService,
      StockMoveRepository stockMoveRepository,
      InvoiceTermService invoiceTermService,
      DeclarationWorkflowService declarationWorkflowService,
      CommonInvoiceService commonInvoiceService,
      InvoiceLineOrderService invoiceLineOrderService,
      SaleInvoicingStateService saleInvoicingStateService,
      AppBusinessProjectService appBusinessProjectService) {
    super(
        appBaseService,
        appSupplychainService,
        declarationRepo,
        invoiceRepo,
        invoiceService,
        declarationLineService,
        stockMoveRepository,
        invoiceTermService,
        declarationWorkflowService,
        commonInvoiceService,
        invoiceLineOrderService,
        saleInvoicingStateService);
    this.appBusinessProjectService = appBusinessProjectService;
  }

  @Transactional(rollbackOn = {Exception.class})
  public Invoice mergeInvoice(
      List<Invoice> invoiceList,
      Company company,
      Currency currency,
      Partner partner,
      Partner contactPartner,
      PriceList priceList,
      PaymentMode paymentMode,
      PaymentCondition paymentCondition,
      TradingName tradingName,
      FiscalPosition fiscalPosition,
      Declaration declaration,
      Project project)
      throws AxelorException {
    Invoice invoiceMerged =
        super.mergeInvoice(
            invoiceList,
            company,
            currency,
            partner,
            contactPartner,
            priceList,
            paymentMode,
            paymentCondition,
            tradingName,
            fiscalPosition,
            declaration);
    if (project != null
        && !appBusinessProjectService.getAppBusinessProject().getProjectInvoiceLines()) {
      invoiceMerged.setProject(project);
      for (InvoiceLine invoiceLine : invoiceMerged.getInvoiceLineList()) {
        invoiceLine.setProject(project);
      }
    }
    return invoiceMerged;
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(
      Invoice invoice, DeclarationLine declarationLine, BigDecimal qtyToInvoice)
      throws AxelorException {
    List<InvoiceLine> invoiceLines = super.createInvoiceLine(invoice, declarationLine, qtyToInvoice);

    if (!appBusinessProjectService.isApp("business-project")) {
      return invoiceLines;
    }

    for (InvoiceLine invoiceLine : invoiceLines) {
      if (declarationLine != null) {
        invoiceLine.setProject(declarationLine.getProject());
      }
    }
    return invoiceLines;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(
      Declaration declaration,
      int operationSelect,
      BigDecimal amount,
      boolean isPercent,
      Map<Long, BigDecimal> qtyToInvoiceMap,
      List<Long> timetableIdList)
      throws AxelorException {
    Invoice invoice =
        super.generateInvoice(
            declaration, operationSelect, amount, isPercent, qtyToInvoiceMap, timetableIdList);
    Project project = declaration.getProject();
    if (project != null) {
      invoice.setProject(project);
    }
    invoiceRepo.save(invoice);
    return invoice;
  }
}
