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

import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.stock.db.LogisticalForm;
import com.axelor.apps.stock.db.LogisticalFormLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.LogisticalFormServiceImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.inject.Beans;
import java.math.BigDecimal;

public class LogisticalFormSupplychainServiceImpl extends LogisticalFormServiceImpl
    implements LogisticalFormSupplychainService {

  @Override
  protected boolean testForDetailLine(StockMoveLine stockMoveLine) {
    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      return super.testForDetailLine(stockMoveLine);
    }
    DeclarationLine declarationLine = stockMoveLine.getDeclarationLine();
    return declarationLine == null
        || declarationLine.getTypeSelect() == DeclarationLineRepository.TYPE_NORMAL;
  }

  @Override
  protected LogisticalFormLine createLogisticalFormLine(
      LogisticalForm logisticalForm, StockMoveLine stockMoveLine, BigDecimal qty) {
    LogisticalFormLine logisticalFormLine =
        super.createLogisticalFormLine(logisticalForm, stockMoveLine, qty);

    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      return logisticalFormLine;
    }

    StockMove stockMove =
        logisticalFormLine.getStockMoveLine() != null
            ? logisticalFormLine.getStockMoveLine().getStockMove()
            : null;

    if (stockMove != null
        && stockMove.getOriginId() != null
        && stockMove.getOriginId() != 0
        && stockMove.getOriginTypeSelect().equals(StockMoveRepository.ORIGIN_SALE_ORDER)) {
      logisticalFormLine.setDeclaration(
          Beans.get(DeclarationRepository.class).find(stockMove.getOriginId()));
    }

    return logisticalFormLine;
  }
}
