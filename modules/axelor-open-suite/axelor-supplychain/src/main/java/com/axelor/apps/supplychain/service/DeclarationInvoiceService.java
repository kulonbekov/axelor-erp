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

import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.DeclarationLineTax;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface DeclarationInvoiceService {

  /**
   * Generate an invoice from a sale order. call {@link
   * DeclarationInvoiceService#createInvoice(Declaration)} to create the invoice.
   *
   * @param declaration
   * @return the generated invoice
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  Invoice generateInvoice(Declaration declaration) throws AxelorException;

  /**
   * Generate an invoice from a sale order. call {@link
   * DeclarationInvoiceService#createInvoice(Declaration, List)} to create the invoice.
   *
   * @param declaration
   * @param declarationLinesSelected
   * @return the generated invoice
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  Invoice generateInvoice(Declaration declaration, List<DeclarationLine> declarationLinesSelected)
      throws AxelorException;

  /**
   * Generate an invoice from a sale order. call {@link
   * DeclarationInvoiceService#createInvoice(Declaration, List, Map)} to create the invoice.
   *
   * @param declaration
   * @param declarationLinesSelected
   * @param qtyToInvoiceMap
   * @return the generated invoice
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  Invoice generateInvoice(
      Declaration declaration,
      List<DeclarationLine> declarationLinesSelected,
      Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException;

  /**
   * Generate invoice from the sale order wizard.
   *
   * @param declaration
   * @param operationSelect
   * @param amount
   * @param isPercent
   * @param qtyToInvoiceMap
   * @return the generated invoice
   * @throws AxelorException
   */
  Invoice generateInvoice(
      Declaration declaration,
      int operationSelect,
      BigDecimal amount,
      boolean isPercent,
      Map<Long, BigDecimal> qtyToInvoiceMap,
      List<Long> timetableIdList)
      throws AxelorException;

  Declaration fillDeclaration(Declaration declaration, Invoice invoice);

  /**
   * Create invoice from a sale order.
   *
   * @param declaration
   * @return the generated invoice
   * @throws AxelorException
   */
  Invoice createInvoice(Declaration declaration) throws AxelorException;

  /**
   * Create invoice from a sale order.
   *
   * @param declaration
   * @return the generated invoice
   * @throws AxelorException
   */
  Invoice createInvoice(Declaration declaration, List<DeclarationLine> declarationLineList)
      throws AxelorException;

  /**
   * Create an invoice.
   *
   * @param declaration the sale order used to create the invoice
   * @param declarationLineList the lines that will be used to create the invoice lines
   * @param qtyToInvoiceMap the quantity used to create the invoice lines
   * @return the generated invoice
   * @throws AxelorException
   */
  Invoice createInvoice(
      Declaration declaration,
      List<DeclarationLine> declarationLineList,
      Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException;

  /**
   * Allows to create an advance payment from a sale order. Creates a one line invoice with the
   * advance payment product.
   *
   * @param declaration
   * @param amountToInvoice
   * @param isPercent
   * @return the generated invoice
   * @throws AxelorException
   */
  Invoice generateAdvancePayment(Declaration declaration, BigDecimal amountToInvoice, boolean isPercent)
      throws AxelorException;

  /**
   * Create sale order lines from tax line a percent to invoice, and a product. Called by {@link
   * #generatePartialInvoice} and {@link #generateAdvancePayment}
   *
   * @param invoice
   * @param taxLineList
   * @param invoicingProduct
   * @param percentToInvoice
   * @return
   */
  List<InvoiceLine> createInvoiceLinesFromTax(
      Invoice invoice,
      List<DeclarationLineTax> taxLineList,
      Product invoicingProduct,
      BigDecimal percentToInvoice)
      throws AxelorException;

  /**
   * Allows to create an invoice from lines with given quantity in sale order. This function checks
   * that the map contains at least one value and convert percent to quantity if necessary.
   *
   * @param declaration
   * @param qtyToInvoiceMap This map links the sale order lines with desired quantities.
   * @param isPercent
   * @return the generated invoice
   * @throws AxelorException
   */
  Invoice generateInvoiceFromLines(
      Declaration declaration, Map<Long, BigDecimal> qtyToInvoiceMap, boolean isPercent)
      throws AxelorException;

  InvoiceGenerator createInvoiceGenerator(Declaration declaration) throws AxelorException;

  InvoiceGenerator createInvoiceGenerator(Declaration declaration, boolean isRefund)
      throws AxelorException;

  /**
   * Creates an invoice line.
   *
   * @param invoice the created line will be linked to this invoice
   * @param declarationLine the sale order line used to generate the invoice line.
   * @param qtyToInvoice the quantity invoiced for this line
   * @return the generated invoice line
   * @throws AxelorException
   */
  List<InvoiceLine> createInvoiceLine(
      Invoice invoice, DeclarationLine declarationLine, BigDecimal qtyToInvoice) throws AxelorException;

  /**
   * Create the lines for the invoice by calling {@link
   * DeclarationInvoiceService#createInvoiceLine(Invoice, DeclarationLine, BigDecimal)}
   *
   * @param invoice the created lines will be linked to this invoice
   * @param declarationLineList the candidate lines used to generate the invoice lines.
   * @param qtyToInvoiceMap the quantities to invoice for each sale order lines. If equals to zero,
   *     the invoice line will not be created
   * @return the generated invoice lines
   * @throws AxelorException
   */
  List<InvoiceLine> createInvoiceLines(
      Invoice invoice, List<DeclarationLine> declarationLineList, Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException;

  /**
   * Use the different parameters to have the price in % of the created invoice.
   *
   * @param declaration the sale order used to get the total price
   * @param amount the amount to invoice
   * @param isPercent true if the amount to invoice is in %
   * @return The % in price which will be used in the created invoice
   * @throws AxelorException if the amount to invoice is larger than the total amount
   */
  BigDecimal computeAmountToInvoicePercent(
      Declaration declaration, BigDecimal amount, boolean isPercent) throws AxelorException;

  BigDecimal computeAmountToInvoice(
      BigDecimal amountToInvoice,
      int operationSelect,
      Declaration declaration,
      Map<Long, BigDecimal> qtyToInvoiceMap,
      Map<Long, BigDecimal> priceMap,
      Map<Long, BigDecimal> qtyMap,
      boolean isPercent)
      throws AxelorException;

  /**
   * Set the updated sale order amount invoiced without checking.
   *
   * @param declaration
   * @param currentInvoiceId
   * @param excludeCurrentInvoice
   * @throws AxelorException
   */
  void update(Declaration declaration, Long currentInvoiceId, boolean excludeCurrentInvoice)
      throws AxelorException;

  BigDecimal getInvoicedAmount(Declaration declaration);

  BigDecimal getInvoicedAmount(
      Declaration declaration, Long currentInvoiceId, boolean excludeCurrentInvoice);

  /**
   * Return all invoices for the given sale order. Beware that some of them may be bound to several
   * orders (through the lines).
   *
   * @param declaration Sale order to get invoices for
   * @return A possibly empty list of invoices related to this order.
   */
  List<Invoice> getInvoices(Declaration declaration);

  @Transactional(rollbackOn = {Exception.class})
  Invoice mergeInvoice(
      List<Invoice> invoiceList,
      Company cmpany,
      Currency currency,
      Partner partner,
      Partner contactPartner,
      PriceList priceList,
      PaymentMode paymentMode,
      PaymentCondition paymentCondition,
      TradingName tradingName,
      FiscalPosition fiscalPosition,
      Declaration declaration)
      throws AxelorException;

  /**
   * @param declaration the sale order from context
   * @return the domain for the operation select field in the invoicing wizard form
   */
  List<Integer> getInvoicingWizardOperationDomain(Declaration declaration);

  /**
   * throw exception if all invoices amount generated from the sale order and amountToInvoice is
   * greater than declaration's amount
   *
   * @param declaration
   * @param amountToInvoice
   * @param isPercent
   * @throws AxelorException
   */
  void displayErrorMessageIfDeclarationIsInvoiceable(
      Declaration declaration, BigDecimal amountToInvoice, boolean isPercent) throws AxelorException;

  /**
   * Display error message if all invoices have been generated for the sale order
   *
   * @param declaration
   * @throws AxelorException
   */
  void displayErrorMessageBtnGenerateInvoice(Declaration declaration) throws AxelorException;

  int getDeclarationInvoicingState(Declaration declaration);
}
