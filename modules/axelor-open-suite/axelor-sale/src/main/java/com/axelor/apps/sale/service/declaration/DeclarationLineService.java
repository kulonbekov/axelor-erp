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

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.sale.db.ComplementaryProduct;
import com.axelor.apps.sale.db.Pack;
import com.axelor.apps.sale.db.PackLine;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DeclarationLineService {

  /**
   * Update all fields of the sale order line from the product.
   *
   * @param declarationLine
   * @param declaration
   */
  void computeProductInformation(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException;

  DeclarationLine resetProductInformation(DeclarationLine line);

  /**
   * Reset price and inTaxPrice (only if the line.enableFreezeField is disabled) of the
   * declarationLine
   *
   * @param line
   */
  void resetPrice(DeclarationLine line);

  /**
   * Compute totals from a sale order line
   *
   * @param declaration
   * @param declarationLine
   * @return
   * @throws AxelorException
   */
  public Map<String, BigDecimal> computeValues(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException;

  /**
   * Compute the excluded tax total amount of a sale order line.
   *
   * @param declarationLine the sale order line which total amount you want to compute.
   * @return The excluded tax total amount.
   */
  public BigDecimal computeAmount(DeclarationLine declarationLine);

  /**
   * Compute the excluded tax total amount of a sale order line.
   *
   * @param quantity The quantity.
   * @param price The unit price.
   * @return The excluded tax total amount.
   */
  public BigDecimal computeAmount(BigDecimal quantity, BigDecimal price);

  public BigDecimal getExTaxUnitPrice(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException;

  public BigDecimal getInTaxUnitPrice(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException;

  public TaxLine getTaxLine(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException;

  public BigDecimal getAmountInCompanyCurrency(BigDecimal exTaxTotal, Declaration declaration)
      throws AxelorException;

  public BigDecimal getCompanyCostPrice(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException;

  public PriceListLine getPriceListLine(
      DeclarationLine declarationLine, PriceList priceList, BigDecimal price);

  /**
   * Compute and return the discounted price of a sale order line.
   *
   * @param declarationLine the sale order line.
   * @param inAti whether or not the sale order line (and thus the discounted price) includes taxes.
   * @return the discounted price of the line, including taxes if inAti is true.
   */
  public BigDecimal computeDiscount(DeclarationLine declarationLine, Boolean inAti);

  public Map<String, Object> getDiscountsFromPriceLists(
      Declaration declaration, DeclarationLine declarationLine, BigDecimal price);

  public int getDiscountTypeSelect(
      Declaration declaration, DeclarationLine declarationLine, BigDecimal price);

  public Unit getSaleUnit(DeclarationLine declarationLine);

  public Declaration getDeclaration(Context context);

  public BigDecimal getAvailableStock(Declaration declaration, DeclarationLine declarationLine);

  public BigDecimal getAllocatedStock(Declaration declaration, DeclarationLine declarationLine);

  public void checkMultipleQty(DeclarationLine declarationLine, ActionResponse response);

  /**
   * Fill price for standard line.
   *
   * @param declarationLine
   * @param declaration
   * @throws AxelorException
   */
  public void fillPrice(DeclarationLine declarationLine, Declaration declaration) throws AxelorException;

  /**
   * Fill the complementaryProductList of the declarationLine from the possible complementary products
   * of the product of the line
   *
   * @param declarationLine
   */
  public void fillComplementaryProductList(DeclarationLine declarationLine);

  public DeclarationLine createDeclarationLine(
      PackLine packLine,
      Declaration declaration,
      BigDecimal packQty,
      BigDecimal conversionRate,
      Integer sequence)
      throws AxelorException;

  /**
   * Get unique values of type field from pack lines
   *
   * @param packLineList
   * @return
   */
  public Set<Integer> getPackLineTypes(List<PackLine> packLineList);

  /**
   * To create non standard DeclarationLine from Pack.
   *
   * @param pack
   * @param declaration
   * @param packQty
   * @param declarationLineList
   * @param sequence
   * @return
   */
  public List<DeclarationLine> createNonStandardSOLineFromPack(
      Pack pack,
      Declaration declaration,
      BigDecimal packQty,
      List<DeclarationLine> declarationLineList,
      Integer sequence);

  /**
   * Finds max discount from product category and his parents, and returns it.
   *
   * @param declaration a sale order (from context or sale order line)
   * @param declarationLine a sale order line
   * @return The maximal discount or null if the value is not needed
   */
  BigDecimal computeMaxDiscount(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException;

  /**
   * Compares sale order line discount with given max discount. Manages the two cases of amount
   * percent and amount fixed.
   *
   * @param declarationLine a sale order line
   * @param maxDiscount a max discount
   * @return whether the discount is greather than the one authorized
   */
  boolean isDeclarationLineDiscountGreaterThanMaxDiscount(
      DeclarationLine declarationLine, BigDecimal maxDiscount);

  /**
   * To create 'Start of pack' and 'End of pack' type {@link DeclarationLine}.
   *
   * @param pack
   * @param declaration
   * @param packQty
   * @param packLine
   * @param typeSelect
   * @param sequence
   * @return
   */
  public DeclarationLine createStartOfPackAndEndOfPackTypeDeclarationLine(
      Pack pack,
      Declaration declaration,
      BigDecimal packQty,
      PackLine packLine,
      Integer typeSelect,
      Integer sequence);

  /**
   * To check that declarationLineList has "End of pack" type line.
   *
   * @param declarationLineList
   * @return
   */
  public boolean hasEndOfPackTypeLine(List<DeclarationLine> declarationLineList);

  /**
   * Update product qty.
   *
   * @param declarationLine
   * @param declaration
   * @param oldQty
   * @param newQty
   * @return {@link DeclarationLine}}
   * @throws AxelorException
   */
  public DeclarationLine updateProductQty(
      DeclarationLine declarationLine, Declaration declaration, BigDecimal oldQty, BigDecimal newQty)
      throws AxelorException;

  /**
   * To check that Start of pack type line quantity changed or not.
   *
   * @param declarationLineList
   * @return
   */
  public boolean isStartOfPackTypeLineQtyChanged(List<DeclarationLine> declarationLineList);

  /**
   * Fill price for standard line from pack line.
   *
   * @param declarationLine
   * @param declaration
   * @return
   * @throws AxelorException
   */
  public void fillPriceFromPackLine(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException;

  /**
   * A function used to get the ex tax unit price of a sale order line from pack line
   *
   * @param declaration the sale order containing the sale order line
   * @param declarationLine
   * @param taxLine
   * @return the ex tax unit price of the sale order line
   * @throws AxelorException
   */
  public BigDecimal getExTaxUnitPriceFromPackLine(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException;

  /**
   * A function used to get the in tax unit price of a sale order line from pack line
   *
   * @param declaration the sale order containing the sale order line
   * @param declarationLine
   * @param taxLine
   * @return the in tax unit price of the sale order line
   * @throws AxelorException
   */
  public BigDecimal getInTaxUnitPriceFromPackLine(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException;

  /**
   * Compute product domain from configurations and sale order.
   *
   * @param declarationLine a sale order line
   * @param declaration a sale order (can be a sale order from context and not from database)
   * @return a String with the JPQL expression used to filter product selection
   */
  String computeProductDomain(DeclarationLine declarationLine, Declaration declaration);

  /**
   * To manage Complementary Product sale order line.
   *
   * @param complementaryProduct
   * @param declaration
   * @param declarationLine
   * @return New complementary sales order lines
   * @throws AxelorException
   */
  public List<DeclarationLine> manageComplementaryProductDeclarationLine(
      ComplementaryProduct complementaryProduct, Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException;

  public List<DeclarationLine> updateLinesAfterFiscalPositionChange(Declaration declaration)
      throws AxelorException;

  /**
   * Methods to compute the pricing scale of declarationLine <br>
   * It is supposed that only one root pricing (pricing with no previousPricing) exists with the
   * configuration of the declarationLine. (product, productCategory, company, concernedModel) Having
   * more than one pricing matched may result on a unexpected result
   *
   * @param declarationLine
   * @throws AxelorException
   */
  public void computePricingScale(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException;

  /**
   * Methods that checks if declarationLine can be can classified with a pricing line of a existing
   * and started pricing. <br>
   * It is supposed that only one root pricing (pricing with no previousPricing) exists with the
   * configuration of the declarationLine. (product, productCategory, company, concernedModel) Having
   * more than one pricing matched may have different result each time this method is called
   *
   * @param declarationLine
   * @param declaration
   * @return true if it can be classified, else false
   * @throws AxelorException
   */
  public boolean hasPricingLine(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException;
}
