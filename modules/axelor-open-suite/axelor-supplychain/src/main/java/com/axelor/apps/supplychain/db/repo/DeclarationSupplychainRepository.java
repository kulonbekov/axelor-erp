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
package com.axelor.apps.supplychain.db.repo;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationManagementRepository;
import com.axelor.apps.supplychain.service.AccountingSituationSupplychainService;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.axelor.inject.Beans;
import com.axelor.studio.app.service.AppService;
import java.math.BigDecimal;
import java.util.Map;

public class DeclarationSupplychainRepository extends DeclarationManagementRepository {

  @Override
  public Declaration copy(Declaration entity, boolean deep) {

    Declaration copy = super.copy(entity, deep);

    if (!Beans.get(AppService.class).isApp("supplychain")) {
      return copy;
    }

    copy.setShipmentDate(null);
    copy.setDeliveryState(DELIVERY_STATE_NOT_DELIVERED);
    copy.setAmountInvoiced(null);
    copy.setStockMoveList(null);

    if (copy.getDeclarationLineList() != null) {
      for (DeclarationLine declarationLine : copy.getDeclarationLineList()) {
        declarationLine.setDeliveryState(null);
        declarationLine.setDeliveredQty(null);
        declarationLine.setAmountInvoiced(null);
        declarationLine.setInvoiced(null);
        declarationLine.setIsInvoiceControlled(null);
        declarationLine.setReservedQty(BigDecimal.ZERO);
      }
    }

    return copy;
  }

  @Override
  public void remove(Declaration order) {

    Partner partner = order.getClientPartner();

    super.remove(order);

    try {
      Beans.get(AccountingSituationSupplychainService.class).updateUsedCredit(partner);
    } catch (AxelorException e) {
      e.printStackTrace();
    }
  }

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    Long declarationId = (Long) json.get("id");
    Declaration declaration = find(declarationId);
    json.put(
        "$invoicingState",
        Beans.get(DeclarationInvoiceService.class).getDeclarationInvoicingState(declaration));
    return super.populate(json, context);
  }
}
