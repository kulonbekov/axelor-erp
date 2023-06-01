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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.service.declaration.DeclarationComputeServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.sale.service.declaration.DeclarationLineTaxService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationComputeServiceSupplychainImpl extends DeclarationComputeServiceImpl {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject
  public DeclarationComputeServiceSupplychainImpl(
      DeclarationLineService declarationLineService, DeclarationLineTaxService declarationLineTaxService) {

    super(declarationLineService, declarationLineTaxService);
  }

  @Override
  public void _computeDeclaration(Declaration declaration) throws AxelorException {

    super._computeDeclaration(declaration);

    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      return;
    }

    int maxDelay = 0;

    if (declaration.getDeclarationLineList() != null && !declaration.getDeclarationLineList().isEmpty()) {
      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {

        if ((declarationLine.getSaleSupplySelect() == DeclarationLineRepository.SALE_SUPPLY_PRODUCE
            || declarationLine.getSaleSupplySelect()
                == DeclarationLineRepository.SALE_SUPPLY_PURCHASE)) {
          maxDelay =
              Integer.max(
                  maxDelay,
                  declarationLine.getStandardDelay() == null ? 0 : declarationLine.getStandardDelay());
        }
      }
    }
    declaration.setStandardDelay(maxDelay);

    if (Beans.get(AppAccountService.class).getAppAccount().getManageAdvancePaymentInvoice()) {
      declaration.setAdvanceTotal(computeTotalInvoiceAdvancePayment(declaration));
    }
    Beans.get(DeclarationSupplychainService.class).updateAmountToBeSpreadOverTheTimetable(declaration);
  }

  protected BigDecimal computeTotalInvoiceAdvancePayment(Declaration declaration) {
    BigDecimal total = BigDecimal.ZERO;

    if (declaration.getId() == null) {
      return total;
    }

    List<Invoice> advancePaymentInvoiceList =
        Beans.get(InvoiceRepository.class)
            .all()
            .filter(
                "self.declaration.id = :declarationId AND self.operationSubTypeSelect = :operationSubTypeSelect")
            .bind("declarationId", declaration.getId())
            .bind("operationSubTypeSelect", InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE)
            .fetch();
    if (advancePaymentInvoiceList == null || advancePaymentInvoiceList.isEmpty()) {
      return total;
    }
    for (Invoice advance : advancePaymentInvoiceList) {
      total = total.add(advance.getAmountPaid());
    }
    return total;
  }
}
