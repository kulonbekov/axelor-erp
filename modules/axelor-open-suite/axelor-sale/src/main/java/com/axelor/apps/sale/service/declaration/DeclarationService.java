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
package com.axelor.apps.sale.service.declaration;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.sale.db.Pack;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public interface DeclarationService {

  public String getFileName(Declaration declaration);

  public Declaration computeEndOfValidityDate(Declaration declaration);

  /**
   * Fill {@link Declaration#mainInvoicingAddressStr} and {@link Declaration#deliveryAddressStr}
   *
   * @param declaration
   */
  public void computeAddressStr(Declaration declaration);

  /**
   * Enable edit order.
   *
   * @param declaration
   * @throws AxelorException
   */
  boolean enableEditOrder(Declaration declaration) throws AxelorException;

  /**
   * Check modified confirmed order before saving it.
   *
   * @param declaration
   * @param declarationView
   * @throws AxelorException
   */
  void checkModifiedConfirmedOrder(Declaration declaration, Declaration declarationView)
      throws AxelorException;

  /**
   * Validate changes.
   *
   * @param declaration
   * @throws AxelorException
   */
  void validateChanges(Declaration declaration) throws AxelorException;

  /**
   * Sort detail lines by sequence.
   *
   * @param declaration
   */
  void sortDeclarationLineList(Declaration declaration);

  /**
   * Convert PackLines of pack into DeclarationLines.
   *
   * @param declaration
   * @throws AxelorException
   */
  Declaration addPack(Declaration declaration, Pack pack, BigDecimal packQty) throws AxelorException;

  /**
   * Handle the creation / updating of complementary products. Called onChange of declarationLineList.
   *
   * @param declaration
   * @return
   */
  public List<DeclarationLine> handleComplementaryProducts(Declaration declaration)
      throws AxelorException;

  /**
   * Blocks if the given sale order has line with a discount superior to the max authorized
   * discount.
   *
   * @param declaration a sale order
   * @throws AxelorException if the sale order is in anomaly
   */
  void checkUnauthorizedDiscounts(Declaration declaration) throws AxelorException;

  /**
   * To update product quantity with pack header quantity.
   *
   * @param declaration
   * @return {@link Declaration}
   * @throws AxelorException
   */
  public Declaration updateProductQtyWithPackHeaderQty(Declaration declaration) throws AxelorException;

  /**
   * To manage Complementary Product sale order lines.
   *
   * @param declaration
   * @throws AxelorException
   */
  public void manageComplementaryProductSOLines(Declaration declaration) throws AxelorException;

  Declaration separateInNewQuotation(
      Declaration declaration, ArrayList<LinkedHashMap<String, Object>> declarationLines)
      throws AxelorException;
}
