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
package com.axelor.apps.supplychain.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.exception.AccountExceptionMessage;
import com.axelor.apps.account.service.invoice.InvoiceMergingServiceImpl;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class InvoiceMergingServiceSupplychainImpl extends InvoiceMergingServiceImpl {

  protected final PurchaseOrderInvoiceService purchaseOrderInvoiceService;
  protected final DeclarationInvoiceService declarationInvoiceService;

  @Inject
  public InvoiceMergingServiceSupplychainImpl(
      InvoiceService invoiceService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      DeclarationInvoiceService declarationInvoiceService) {
    super(invoiceService);
    this.purchaseOrderInvoiceService = purchaseOrderInvoiceService;
    this.declarationInvoiceService = declarationInvoiceService;
  }

  protected static class CommonFieldsSupplychainImpl extends CommonFieldsImpl {
    private Declaration commonDeclaration;
    private PurchaseOrder commonPurchaseOrder;

    public Declaration getCommonDeclaration() {
      return commonDeclaration;
    }

    public void setCommonDeclaration(Declaration commonDeclaration) {
      this.commonDeclaration = commonDeclaration;
    }

    public PurchaseOrder getCommonPurchaseOrder() {
      return commonPurchaseOrder;
    }

    public void setCommonPurchaseOrder(PurchaseOrder commonPurchaseOrder) {
      this.commonPurchaseOrder = commonPurchaseOrder;
    }
  }

  protected static class ChecksSupplychainImpl extends ChecksImpl {
    private boolean existDeclarationDiff = false;
    private boolean existPurchaseOrderDiff = false;

    public boolean isExistDeclarationDiff() {
      return existDeclarationDiff;
    }

    public void setExistDeclarationDiff(boolean existDeclarationDiff) {
      this.existDeclarationDiff = existDeclarationDiff;
    }

    public boolean isExistPurchaseOrderDiff() {
      return existPurchaseOrderDiff;
    }

    public void setExistPurchaseOrderDiff(boolean existPurchaseOrderDiff) {
      this.existPurchaseOrderDiff = existPurchaseOrderDiff;
    }
  }

  protected static class InvoiceMergingResultSupplychainImpl extends InvoiceMergingResultImpl {
    private final CommonFieldsSupplychainImpl commonFields;
    private final ChecksSupplychainImpl checks;

    public InvoiceMergingResultSupplychainImpl() {
      super();
      this.commonFields = new CommonFieldsSupplychainImpl();
      this.checks = new ChecksSupplychainImpl();
    }
  }

  @Override
  public InvoiceMergingResultSupplychainImpl create() {
    return new InvoiceMergingResultSupplychainImpl();
  }

  @Override
  public CommonFieldsSupplychainImpl getCommonFields(InvoiceMergingResult result) {
    return ((InvoiceMergingResultSupplychainImpl) result).commonFields;
  }

  @Override
  public ChecksSupplychainImpl getChecks(InvoiceMergingResult result) {
    return ((InvoiceMergingResultSupplychainImpl) result).checks;
  }

  @Override
  protected void extractFirstNonNullCommonFields(
      List<Invoice> invoicesToMerge, InvoiceMergingResult result) {
    super.extractFirstNonNullCommonFields(invoicesToMerge, result);
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)) {
      invoicesToMerge.stream()
          .map(Invoice::getDeclaration)
          .filter(Objects::nonNull)
          .findFirst()
          .ifPresent(it -> getCommonFields(result).setCommonDeclaration(it));
    }
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE)) {
      invoicesToMerge.stream()
          .map(Invoice::getPurchaseOrder)
          .filter(Objects::nonNull)
          .findFirst()
          .ifPresent(it -> getCommonFields(result).setCommonPurchaseOrder(it));
    }
  }

  @Override
  protected void fillCommonFields(Invoice invoice, InvoiceMergingResult result) {
    super.fillCommonFields(invoice, result);
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)) {
      if (getCommonFields(result).getCommonDeclaration() != null
          && !getCommonFields(result).getCommonDeclaration().equals(invoice.getDeclaration())) {
        getCommonFields(result).setCommonDeclaration(null);
        getChecks(result).setExistDeclarationDiff(true);
      }
    }
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE)) {
      if (getCommonFields(result).getCommonPurchaseOrder() != null
          && !getCommonFields(result).getCommonPurchaseOrder().equals(invoice.getPurchaseOrder())) {
        getCommonFields(result).setCommonPurchaseOrder(null);
        getChecks(result).setExistPurchaseOrderDiff(true);
      }
    }
  }

  @Override
  protected void checkErrors(StringJoiner fieldErrors, InvoiceMergingResult result)
      throws AxelorException {
    super.checkErrors(fieldErrors, result);
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)) {
      if (getCommonFields(result).getCommonDeclaration() == null
          && getChecks(result).isExistDeclarationDiff()) {
        fieldErrors.add(I18n.get(AccountExceptionMessage.INVOICE_MERGE_ERROR_DECLARATION));
      }
    }
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE)) {
      if (getCommonFields(result).getCommonPurchaseOrder() == null
          && getChecks(result).isExistPurchaseOrderDiff()) {
        fieldErrors.add(I18n.get(AccountExceptionMessage.INVOICE_MERGE_ERROR_PURCHASEORDER));
      }
    }
  }

  @Override
  protected Invoice generateMergedInvoice(
      List<Invoice> invoicesToMerge, InvoiceMergingResult result) throws AxelorException {
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE)) {
      return purchaseOrderInvoiceService.mergeInvoice(
          invoicesToMerge,
          getCommonFields(result).getCommonCompany(),
          getCommonFields(result).getCommonCurrency(),
          getCommonFields(result).getCommonPartner(),
          getCommonFields(result).getCommonContactPartner(),
          getCommonFields(result).getCommonPriceList(),
          getCommonFields(result).getCommonPaymentMode(),
          getCommonFields(result).getCommonPaymentCondition(),
          getCommonFields(result).getCommonTradingName(),
          getCommonFields(result).getCommonFiscalPosition(),
          getCommonFields(result).getCommonSupplierInvoiceNb(),
          getCommonFields(result).getCommonOriginDate(),
          getCommonFields(result).getCommonPurchaseOrder());
    }
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)) {
      return declarationInvoiceService.mergeInvoice(
          invoicesToMerge,
          getCommonFields(result).getCommonCompany(),
          getCommonFields(result).getCommonCurrency(),
          getCommonFields(result).getCommonPartner(),
          getCommonFields(result).getCommonContactPartner(),
          getCommonFields(result).getCommonPriceList(),
          getCommonFields(result).getCommonPaymentMode(),
          getCommonFields(result).getCommonPaymentCondition(),
          getCommonFields(result).getCommonTradingName(),
          getCommonFields(result).getCommonFiscalPosition(),
          getCommonFields(result).getCommonDeclaration());
    }
    return null;
  }
}
