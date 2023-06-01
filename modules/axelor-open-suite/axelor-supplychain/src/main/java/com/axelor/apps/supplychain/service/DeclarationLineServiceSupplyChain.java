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
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import java.math.BigDecimal;
import java.util.List;

public interface DeclarationLineServiceSupplyChain extends DeclarationLineService {

  int SALE_ORDER_LINE_NOT_INVOICED = 1;
  int SALE_ORDER_LINE_PARTIALLY_INVOICED = 2;
  int SALE_ORDER_LINE_INVOICED = 3;

  /**
   * Compute undelivered quantity.
   *
   * @param declarationLine
   * @return
   */
  BigDecimal computeUndeliveredQty(DeclarationLine declarationLine);

  /**
   * Get a list of supplier partner ids available for the product in the sale order line.
   *
   * @param declarationLine
   * @return the list of ids
   */
  List<Long> getSupplierPartnerList(DeclarationLine declarationLine);

  /**
   * Update delivery state.
   *
   * @param declarationLine
   */
  void updateDeliveryState(DeclarationLine declarationLine);

  /**
   * Update delivery states.
   *
   * @param declarationLineList
   */
  void updateDeliveryStates(List<DeclarationLine> declarationLineList);

  /**
   * Create a query to find sale order line of a product of a specific/all company and a
   * specific/all stock location
   *
   * @param productId
   * @param companyId
   * @param stockLocationId
   * @return the query.
   */
  String getDeclarationLineListForAProduct(Long productId, Long companyId, Long stockLocationId);

  /**
   * check qty when modifying declarationLine which is invoiced or delivered
   *
   * @param declarationLine
   */
  BigDecimal checkInvoicedOrDeliveredOrderQty(DeclarationLine declarationLine);

  /**
   * Compute analytic distribution for every analytic move line
   *
   * @param declarationLine
   */
  public DeclarationLine computeAnalyticDistribution(DeclarationLine declarationLine);

  /**
   * Update stock move lines linked to this sale order line by using estimated delivery date as date
   * used for reservation. Do nothing if the configuration is not set to use estimated delivery
   * date.
   *
   * @param declarationLine a sale order line managed by hibernate
   */
  void updateStockMoveReservationDateTime(DeclarationLine declarationLine) throws AxelorException;

  public DeclarationLine createAnalyticDistributionWithTemplate(DeclarationLine declarationLine);

  int getDeclarationLineInvoicingState(DeclarationLine declarationLine);
}
