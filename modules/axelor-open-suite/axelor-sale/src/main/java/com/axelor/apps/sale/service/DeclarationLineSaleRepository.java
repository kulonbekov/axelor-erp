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
package com.axelor.apps.sale.service;

import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import java.util.Map;

public class DeclarationLineSaleRepository extends DeclarationLineRepository {

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    if (context.get("_model") != null
        && context.get("_model").toString().contains("Declaration")
        && context.get("id") != null) {
      Long id = (Long) json.get("id");
      if (id != null) {
        DeclarationLine declarationLine = find(id);
        json.put(
            "$hasWarning",
            declarationLine.getDeclaration() != null
                && (declarationLine.getDeclaration().getStatusSelect()
                        == DeclarationRepository.STATUS_DRAFT_QUOTATION
                    || (declarationLine.getDeclaration().getStatusSelect()
                            == DeclarationRepository.STATUS_ORDER_CONFIRMED
                        && declarationLine.getDeclaration().getOrderBeingEdited()))
                && declarationLine.getDiscountsNeedReview());
      }
    }
    return super.populate(json, context);
  }
}
