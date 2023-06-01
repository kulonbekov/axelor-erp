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
package com.axelor.apps.supplychain.service.batch;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.db.repo.ExceptionOriginRepository;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.axelor.apps.supplychain.service.invoice.SubscriptionInvoiceService;
import com.axelor.db.JPA;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchInvoicing extends BatchStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private SubscriptionInvoiceService subscriptionInvoiceService;

  @Inject
  public BatchInvoicing(DeclarationInvoiceService declarationInvoiceService) {

    super(declarationInvoiceService);
  }

  @Override
  protected void process() {

    List<Declaration> declarations = subscriptionInvoiceService.getSubscriptionOrders(FETCH_LIMIT);

    while (!declarations.isEmpty()) {
      for (Declaration declaration : declarations) {
        try {
          subscriptionInvoiceService.generateSubscriptionInvoice(declaration);
          updateDeclaration(declaration);
        } catch (AxelorException e) {
          TraceBackService.trace(
              new AxelorException(
                  e, e.getCategory(), I18n.get("Order %s"), declaration.getDeclarationSeq()),
              ExceptionOriginRepository.INVOICE_ORIGIN,
              batch.getId());
          incrementAnomaly();
        } catch (Exception e) {
          TraceBackService.trace(
              new Exception(String.format(I18n.get("Order %s"), declaration.getDeclarationSeq()), e),
              ExceptionOriginRepository.INVOICE_ORIGIN,
              batch.getId());
          incrementAnomaly();

          LOG.error("Bug(Anomalie) généré(e) pour le devis {}", declaration.getDeclarationSeq());
        }
      }
      JPA.clear();
      declarations = subscriptionInvoiceService.getSubscriptionOrders(FETCH_LIMIT);
    }
  }

  /**
   * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the
   * entity in the persistent context. Warning : {@code batch} entity have to be saved before.
   */
  @Override
  protected void stop() {

    String comment = I18n.get(SupplychainExceptionMessage.BATCH_INVOICING_1) + " ";
    comment +=
        String.format(
            "\t* %s " + I18n.get(SupplychainExceptionMessage.BATCH_INVOICING_2) + "\n",
            batch.getDone());
    comment +=
        String.format(
            "\t" + I18n.get(BaseExceptionMessage.ALARM_ENGINE_BATCH_4), batch.getAnomaly());

    super.stop();
    addComment(comment);
  }

  protected void setBatchTypeSelect() {
    this.batch.setBatchTypeSelect(BatchRepository.BATCH_TYPE_SALE_BATCH);
  }
}
