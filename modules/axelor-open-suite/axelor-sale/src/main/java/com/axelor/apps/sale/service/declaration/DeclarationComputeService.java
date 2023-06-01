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
import com.axelor.apps.sale.db.Declaration;
import java.math.BigDecimal;

public interface DeclarationComputeService {

  public Declaration _computeDeclarationLineList(Declaration declaration) throws AxelorException;

  public Declaration computeDeclaration(Declaration declaration) throws AxelorException;

  /**
   * Peupler un devis.
   *
   * <p>Cette fonction permet de déterminer les tva d'un devis.
   *
   * @param declaration
   * @throws AxelorException
   */
  public void _populateDeclaration(Declaration declaration) throws AxelorException;

  /**
   * Calculer le montant d'une facture.
   *
   * <p>Le calcul est basé sur les lignes de TVA préalablement créées.
   *
   * @param invoice
   * @param vatLines
   * @throws AxelorException
   */
  public void _computeDeclaration(Declaration declaration) throws AxelorException;

  /**
   * Permet de réinitialiser la liste des lignes de TVA
   *
   * @param declaration Un devis
   */
  public void initDeclarationLineTaxList(Declaration declaration);

  /**
   * Return the total price, computed from the lines. This price is usually equals to {@link
   * Declaration#exTaxTotal} but not in all cases.
   *
   * @param declaration
   * @return total price from the sale order lines
   */
  public BigDecimal getTotalDeclarationPrice(Declaration declaration);

  /**
   * Calculate pack total in sale order lines
   *
   * @param declaration
   */
  public void computePackTotal(Declaration declaration);

  /**
   * Reset pack total in sale order lines
   *
   * @param declaration
   */
  public void resetPackTotal(Declaration declaration);
}
