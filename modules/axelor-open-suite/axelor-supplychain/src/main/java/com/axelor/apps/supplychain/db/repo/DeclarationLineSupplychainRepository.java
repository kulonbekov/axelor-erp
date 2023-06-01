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

import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.DeclarationLineSaleRepository;
import com.axelor.apps.supplychain.service.DeclarationLineServiceSupplyChainImpl;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import java.math.BigDecimal;
import java.util.Map;

public class DeclarationLineSupplychainRepository extends DeclarationLineSaleRepository {

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    Long declarationLineId = (Long) json.get("id");
    DeclarationLine declarationLine = find(declarationLineId);

    Declaration declaration = declarationLine.getDeclaration();

    if (this.availabilityIsNotManaged(declarationLine, declaration)) {
      return super.populate(json, context);
    }

    BigDecimal availableStock =
        Beans.get(DeclarationLineServiceSupplyChainImpl.class)
            .getAvailableStock(declaration, declarationLine);
    BigDecimal allocatedStock =
        Beans.get(DeclarationLineServiceSupplyChainImpl.class)
            .getAllocatedStock(declaration, declarationLine);

    BigDecimal availableQty = availableStock.add(allocatedStock);
    BigDecimal realQty = declarationLine.getQty();

    if (availableQty.compareTo(realQty) >= 0) {
      declarationLine.setAvailableStatus(I18n.get("Available"));
      declarationLine.setAvailableStatusSelect(DeclarationLineRepository.STATUS_AVAILABLE);
    } else if (availableQty.compareTo(realQty) < 0) {
      declarationLine.setAvailableStatus(
          I18n.get("Missing") + " (" + availableQty.subtract(realQty) + ")");
      declarationLine.setAvailableStatusSelect(DeclarationLineRepository.STATUS_MISSING);
    }
    json.put("availableStatus", declarationLine.getAvailableStatus());
    json.put("availableStatusSelect", declarationLine.getAvailableStatusSelect());

    return super.populate(json, context);
  }

  protected boolean availabilityIsNotManaged(DeclarationLine declarationLine, Declaration declaration) {
    return declaration == null
        || declarationLine.getTypeSelect() != DeclarationLineRepository.TYPE_NORMAL
        || declaration.getStatusSelect() != DeclarationRepository.STATUS_ORDER_CONFIRMED
        || declaration.getStockLocation() == null
        || declarationLine.getDeliveryState() == DeclarationLineRepository.DELIVERY_STATE_DELIVERED
        || (declarationLine.getProduct() != null
            && declarationLine
                .getProduct()
                .getProductTypeSelect()
                .equals(ProductRepository.PRODUCT_TYPE_SERVICE));
  }
}
