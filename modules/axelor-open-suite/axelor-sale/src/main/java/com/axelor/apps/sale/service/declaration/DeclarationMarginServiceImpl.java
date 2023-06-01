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
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationMarginServiceImpl implements com.axelor.apps.sale.service.declaration.DeclarationMarginService {

  protected final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected AppSaleService appSaleService;
  protected CurrencyService currencyService;
  protected ProductCompanyService productCompanyService;

  @Inject
  public DeclarationMarginServiceImpl(
      AppSaleService appSaleService,
      CurrencyService currencyService,
      ProductCompanyService productCompanyService) {
    this.appSaleService = appSaleService;
    this.currencyService = currencyService;
    this.productCompanyService = productCompanyService;
  }

  @Override
  public void computeMarginDeclaration(Declaration declaration) {
    BigDecimal accountedRevenue = BigDecimal.ZERO;
    BigDecimal totalCostPrice = BigDecimal.ZERO;
    BigDecimal totalGrossMargin = BigDecimal.ZERO;

    if (declaration.getDeclarationLineList() != null && !declaration.getDeclarationLineList().isEmpty()) {
      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
        if (declarationLine.getProduct() == null
            || (declarationLine.getExTaxTotal().compareTo(BigDecimal.ZERO) == 0
                && !appSaleService.getAppSale().getConsiderZeroCost())) {
          continue;
        }
        totalGrossMargin = totalGrossMargin.add(declarationLine.getSubTotalGrossMargin());
        totalCostPrice = totalCostPrice.add(declarationLine.getSubTotalCostPrice());
        accountedRevenue = accountedRevenue.add(declarationLine.getCompanyExTaxTotal());
      }
    }

    setDeclarationMarginInfo(
        declaration,
        accountedRevenue,
        totalCostPrice,
        totalGrossMargin,
        computeRate(accountedRevenue, totalGrossMargin),
        computeRate(totalCostPrice, totalGrossMargin));
  }

  @Override
  public void computeSubMargin(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException {

    Company company = declaration.getCompany();
    Product product = declarationLine.getProduct();

    BigDecimal exTaxTotal = declarationLine.getExTaxTotal();
    BigDecimal subTotalCostPrice = declarationLine.getSubTotalCostPrice();
    BigDecimal subTotalGrossMargin = BigDecimal.ZERO;
    BigDecimal subMarginRate = BigDecimal.ZERO;
    BigDecimal totalWT =
        currencyService.getAmountCurrencyConvertedAtDate(
            declaration.getCurrency(), company.getCurrency(), exTaxTotal, null);

    if (product != null
        && exTaxTotal.compareTo(BigDecimal.ZERO) != 0
        && subTotalCostPrice.compareTo(BigDecimal.ZERO) != 0) {
      subTotalGrossMargin = totalWT.subtract(subTotalCostPrice);
      subMarginRate = computeRate(totalWT, subTotalGrossMargin);
    }

    if (appSaleService.getAppSale().getConsiderZeroCost()
        && (exTaxTotal.compareTo(BigDecimal.ZERO) == 0
            || subTotalCostPrice.compareTo(BigDecimal.ZERO) == 0)) {
      subTotalGrossMargin = exTaxTotal.subtract(subTotalCostPrice);
      subMarginRate = computeRate(exTaxTotal, subTotalGrossMargin);
    }

    BigDecimal subMarkup = computeRate(subTotalCostPrice, subTotalGrossMargin);
    setDeclarationLineMarginInfo(declarationLine, subTotalGrossMargin, subMarginRate, subMarkup);
  }

  @Override
  public Map<String, BigDecimal> getDeclarationLineComputedMarginInfo(
      Declaration declaration, DeclarationLine declarationLine) throws AxelorException {
    HashMap<String, BigDecimal> map = new HashMap<>();
    computeSubMargin(declaration, declarationLine);
    map.put("subTotalGrossMargin", declarationLine.getSubTotalGrossMargin());
    map.put("subMarginRate", declarationLine.getSubMarginRate());
    map.put("subTotalMarkup", declarationLine.getSubTotalMarkup());
    return map;
  }

  protected BigDecimal computeRate(BigDecimal saleCostPrice, BigDecimal totalGrossMargin) {
    BigDecimal rate = BigDecimal.ZERO;
    if (saleCostPrice.compareTo(BigDecimal.ZERO) != 0) {
      rate =
          totalGrossMargin
              .multiply(new BigDecimal(100))
              .divide(
                  saleCostPrice, AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
    }
    return rate;
  }

  protected void setDeclarationLineMarginInfo(
      DeclarationLine declarationLine,
      BigDecimal subTotalGrossMargin,
      BigDecimal subMarginRate,
      BigDecimal subTotalMarkup) {
    declarationLine.setSubTotalGrossMargin(subTotalGrossMargin);
    declarationLine.setSubMarginRate(subMarginRate);
    declarationLine.setSubTotalMarkup(subTotalMarkup);
  }

  protected void setDeclarationMarginInfo(
      Declaration declaration,
      BigDecimal accountedRevenue,
      BigDecimal totalCostPrice,
      BigDecimal totalGrossMargin,
      BigDecimal marginRate,
      BigDecimal markup) {
    declaration.setAccountedRevenue(accountedRevenue);
    declaration.setTotalCostPrice(totalCostPrice);
    declaration.setTotalGrossMargin(totalGrossMargin);
    declaration.setMarginRate(marginRate);
    declaration.setMarkup(markup);
  }
}
