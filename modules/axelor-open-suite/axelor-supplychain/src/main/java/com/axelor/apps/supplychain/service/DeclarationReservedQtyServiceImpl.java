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
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DeclarationReservedQtyServiceImpl implements DeclarationReservedQtyService {

  protected ReservedQtyService reservedQtyService;
  protected StockMoveLineRepository stockMoveLineRepository;

  @Inject
  public DeclarationReservedQtyServiceImpl(
      ReservedQtyService reservedQtyService, StockMoveLineRepository stockMoveLineRepository) {
    this.reservedQtyService = reservedQtyService;
    this.stockMoveLineRepository = stockMoveLineRepository;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void allocateAll(Declaration declaration) throws AxelorException {
    for (DeclarationLine declarationLine : getNonDeliveredLines(declaration)) {
      reservedQtyService.allocateAll(declarationLine);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void deallocateAll(Declaration declaration) throws AxelorException {
    for (DeclarationLine declarationLine : getNonDeliveredLines(declaration)) {
      reservedQtyService.updateReservedQty(declarationLine, BigDecimal.ZERO);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void reserveAll(Declaration declaration) throws AxelorException {
    for (DeclarationLine declarationLine : getNonDeliveredLines(declaration)) {
      reservedQtyService.requestQty(declarationLine);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelReservation(Declaration declaration) throws AxelorException {
    for (DeclarationLine declarationLine : getNonDeliveredLines(declaration)) {
      reservedQtyService.cancelReservation(declarationLine);
    }
  }

  protected List<DeclarationLine> getNonDeliveredLines(Declaration declaration) {
    List<DeclarationLine> declarationLineList =
        declaration.getDeclarationLineList() == null
            ? new ArrayList<>()
            : declaration.getDeclarationLineList();
    return declarationLineList.stream()
        .filter(declarationLine -> getPlannedStockMoveLine(declarationLine) != null)
        .collect(Collectors.toList());
  }

  protected StockMoveLine getPlannedStockMoveLine(DeclarationLine declarationLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.declarationLine = :declarationLine " + "AND self.stockMove.statusSelect = :planned")
        .bind("declarationLine", declarationLine)
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .fetchOne();
  }
}
