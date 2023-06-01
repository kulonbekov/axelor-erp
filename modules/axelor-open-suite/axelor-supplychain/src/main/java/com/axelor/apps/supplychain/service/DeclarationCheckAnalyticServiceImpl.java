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

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.i18n.I18n;
import java.util.ArrayList;
import java.util.List;

public class DeclarationCheckAnalyticServiceImpl implements DeclarationCheckAnalyticService {

  @Override
  public void checkDeclarationLinesAnalyticDistribution(Declaration declaration) throws AxelorException {

    List<String> productList = new ArrayList<>();
    for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
      if (declarationLine.getTypeSelect() == DeclarationLineRepository.TYPE_NORMAL
          && declarationLine.getAnalyticDistributionTemplate() == null) {
        productList.add(declarationLine.getProductName());
      }
    }
    if (!productList.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(SupplychainExceptionMessage.SALE_ORDER_ANALYTIC_DISTRIBUTION_ERROR),
          productList);
    }
  }
}
