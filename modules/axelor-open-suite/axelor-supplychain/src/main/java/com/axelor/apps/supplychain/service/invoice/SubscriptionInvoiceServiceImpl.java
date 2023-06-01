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
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.Query;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubscriptionInvoiceServiceImpl implements SubscriptionInvoiceService {

  @Inject private AppBaseService appBaseService;

  @Inject private DeclarationRepository declarationRepo;

  @Inject private DeclarationInvoiceService declarationInvoiceService;

  @Override
  public List<Invoice> generateSubscriptionInvoices() throws AxelorException {

    List<Invoice> invoices = new ArrayList<>();

    for (Declaration declaration : getSubscriptionOrders(null)) {
      Invoice invoice = generateSubscriptionInvoice(declaration);
      invoices.add(invoice);
    }

    return invoices;
  }

  @Override
  public List<Declaration> getSubscriptionOrders(Integer limit) {

    Query<Declaration> query =
        declarationRepo
            .all()
            .filter(
                "self.declarationTypeSelect = :declarationType "
                    + "AND self.statusSelect = :declarationStatus "
                    + "AND :subScriptionDate >= self.nextInvoicingDate "
                    + "AND (self.contractEndDate IS NULL OR self.contractEndDate >= :subScriptionDate)")
            .bind("declarationType", DeclarationRepository.SALE_ORDER_TYPE_SUBSCRIPTION)
            .bind("declarationStatus", DeclarationRepository.STATUS_ORDER_CONFIRMED)
            .bind(
                "subScriptionDate",
                appBaseService.getTodayDate(
                    Optional.ofNullable(AuthUtils.getUser())
                        .map(User::getActiveCompany)
                        .orElse(null)));

    if (limit != null) {
      return query.fetch(limit);
    }

    return query.fetch();
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateSubscriptionInvoice(Declaration declaration) throws AxelorException {

    TemporalUnit temporalUnit = ChronoUnit.MONTHS;

    Invoice invoice =
        declarationInvoiceService.generateInvoice(declarationRepo.find(declaration.getId()));

    if (invoice != null) {
      if (declaration.getPeriodicityTypeSelect() == 1) {
        temporalUnit = ChronoUnit.DAYS;
      }
      invoice.setInvoiceDate(appBaseService.getTodayDate(declaration.getCompany()));
      invoice.setOperationSubTypeSelect(InvoiceRepository.OPERATION_SUB_TYPE_SUBSCRIPTION);

      LocalDate invoicingPeriodStartDate = declaration.getNextInvoicingStartPeriodDate();
      invoice.setSubscriptionFromDate(invoicingPeriodStartDate);
      invoice.setSubscriptionToDate(declaration.getNextInvoicingEndPeriodDate());
      if (invoicingPeriodStartDate != null) {
        LocalDate nextInvoicingStartPeriodDate =
            invoicingPeriodStartDate.plus(declaration.getNumberOfPeriods(), temporalUnit);
        declaration.setNextInvoicingStartPeriodDate(nextInvoicingStartPeriodDate);
        LocalDate nextInvoicingEndPeriodDate =
            nextInvoicingStartPeriodDate
                .plus(declaration.getNumberOfPeriods(), temporalUnit)
                .minusDays(1);
        declaration.setNextInvoicingEndPeriodDate(nextInvoicingEndPeriodDate);
      }

      LocalDate nextInvoicingDate = declaration.getNextInvoicingDate();
      if (nextInvoicingDate != null) {
        nextInvoicingDate = nextInvoicingDate.plus(declaration.getNumberOfPeriods(), temporalUnit);
      }
      declaration.setNextInvoicingDate(nextInvoicingDate);
    }

    return invoice;
  }
}
