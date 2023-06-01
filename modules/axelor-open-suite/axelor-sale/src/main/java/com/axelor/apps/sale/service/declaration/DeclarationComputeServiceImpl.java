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
import com.axelor.apps.sale.db.AdvancePayment;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.DeclarationLineTax;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.common.ObjectUtils;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationComputeServiceImpl implements DeclarationComputeService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected DeclarationLineService declarationLineService;
  protected DeclarationLineTaxService declarationLineTaxService;

  @Inject
  public DeclarationComputeServiceImpl(
      DeclarationLineService declarationLineService, DeclarationLineTaxService declarationLineTaxService) {

    this.declarationLineService = declarationLineService;
    this.declarationLineTaxService = declarationLineTaxService;
  }

  @Override
  public Declaration _computeDeclarationLineList(Declaration declaration) throws AxelorException {

    if (declaration.getDeclarationLineList() != null) {
      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
        declarationLine.setCompanyExTaxTotal(
            declarationLineService.getAmountInCompanyCurrency(
                declarationLine.getExTaxTotal(), declaration));
      }
    }

    return declaration;
  }

  @Override
  public Declaration computeDeclaration(Declaration declaration) throws AxelorException {

    this.initDeclarationLineTaxList(declaration);

    this._computeDeclarationLineList(declaration);

    this._populateDeclaration(declaration);

    this._computeDeclaration(declaration);

    return declaration;
  }

  /**
   * Peupler un devis.
   *
   * <p>Cette fonction permet de déterminer les tva d'un devis.
   *
   * @param declaration
   * @throws AxelorException
   */
  @Override
  public void _populateDeclaration(Declaration declaration) throws AxelorException {
    if (declaration.getDeclarationLineList() == null) {
      declaration.setDeclarationLineList(new ArrayList<>());
    }

    if (declaration.getDeclarationLineTaxList() == null) {
      declaration.setDeclarationLineTaxList(new ArrayList<>());
    }

    logger.debug(
        "Populate a sale order => sale order lines : {}",
        new Object[] {declaration.getDeclarationLineList().size()});

    // create Tva lines
    if (declaration.getClientPartner() != null) {
      declaration
          .getDeclarationLineTaxList()
          .addAll(
              declarationLineTaxService.createsDeclarationLineTax(
                  declaration, declaration.getDeclarationLineList()));
    }
  }

  /**
   * Compute the sale order total amounts
   *
   * @param declaration
   * @throws AxelorException
   */
  @Override
  public void _computeDeclaration(Declaration declaration) throws AxelorException {

    declaration.setExTaxTotal(BigDecimal.ZERO);
    declaration.setCompanyExTaxTotal(BigDecimal.ZERO);
    declaration.setTaxTotal(BigDecimal.ZERO);
    declaration.setInTaxTotal(BigDecimal.ZERO);

    for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {

      // skip title lines in computing total amounts
      if (declarationLine.getTypeSelect() != DeclarationLineRepository.TYPE_NORMAL) {
        continue;
      }
      declaration.setExTaxTotal(declaration.getExTaxTotal().add(declarationLine.getExTaxTotal()));

      // In the company accounting currency
      declaration.setCompanyExTaxTotal(
          declaration.getCompanyExTaxTotal().add(declarationLine.getCompanyExTaxTotal()));
    }

    for (DeclarationLineTax declarationLineVat : declaration.getDeclarationLineTaxList()) {

      // In the sale order currency
      declaration.setTaxTotal(declaration.getTaxTotal().add(declarationLineVat.getTaxTotal()));
    }

    declaration.setInTaxTotal(declaration.getExTaxTotal().add(declaration.getTaxTotal()));
    declaration.setAdvanceTotal(computeTotalAdvancePayment(declaration));
    logger.debug(
        "Invoice's total: W.T.T. = {},  W.T. = {}, Tax = {}, A.T.I. = {}",
        new Object[] {
          declaration.getExTaxTotal(), declaration.getTaxTotal(), declaration.getInTaxTotal()
        });
  }

  protected BigDecimal computeTotalAdvancePayment(Declaration declaration) {
    List<AdvancePayment> advancePaymentList = declaration.getAdvancePaymentList();
    BigDecimal total = BigDecimal.ZERO;
    if (advancePaymentList == null || advancePaymentList.isEmpty()) {
      return total;
    }
    for (AdvancePayment advancePayment : advancePaymentList) {
      total = total.add(advancePayment.getAmount());
    }
    return total;
  }

  /**
   * Permet de réinitialiser la liste des lignes de TVA
   *
   * @param declaration Un devis
   */
  @Override
  public void initDeclarationLineTaxList(Declaration declaration) {

    if (declaration.getDeclarationLineTaxList() == null) {
      declaration.setDeclarationLineTaxList(new ArrayList<DeclarationLineTax>());
    } else {
      declaration.getDeclarationLineTaxList().clear();
    }
  }

  @Override
  public BigDecimal getTotalDeclarationPrice(Declaration declaration) {
    BigDecimal price = BigDecimal.ZERO;
    for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
      price = price.add(declarationLine.getQty().multiply(declarationLine.getPriceDiscounted()));
    }
    return price;
  }

  @Override
  public void computePackTotal(Declaration declaration) {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();

    if (ObjectUtils.isEmpty(declarationLineList)
        || !declarationLineService.hasEndOfPackTypeLine(declarationLineList)) {
      return;
    }
    BigDecimal totalExTaxTotal = BigDecimal.ZERO;
    BigDecimal totalInTaxTotal = BigDecimal.ZERO;
    declarationLineList.sort(Comparator.comparing(DeclarationLine::getSequence));
    for (DeclarationLine declarationLine : declarationLineList) {

      switch (declarationLine.getTypeSelect()) {
        case DeclarationLineRepository.TYPE_NORMAL:
          totalExTaxTotal = totalExTaxTotal.add(declarationLine.getExTaxTotal());
          totalInTaxTotal = totalInTaxTotal.add(declarationLine.getInTaxTotal());
          break;

        case DeclarationLineRepository.TYPE_TITLE:
          break;

        case DeclarationLineRepository.TYPE_START_OF_PACK:
          totalExTaxTotal = totalInTaxTotal = BigDecimal.ZERO;
          break;

        case DeclarationLineRepository.TYPE_END_OF_PACK:
          declarationLine.setQty(BigDecimal.ZERO);
          declarationLine.setExTaxTotal(
              declarationLine.getIsShowTotal() ? totalExTaxTotal : BigDecimal.ZERO);
          declarationLine.setInTaxTotal(
              declarationLine.getIsShowTotal() ? totalInTaxTotal : BigDecimal.ZERO);
          totalExTaxTotal = totalInTaxTotal = BigDecimal.ZERO;
          break;

        default:
          break;
      }
    }
    declaration.setDeclarationLineList(declarationLineList);
  }

  @Override
  public void resetPackTotal(Declaration declaration) {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (ObjectUtils.isEmpty(declarationLineList)) {
      return;
    }
    for (DeclarationLine declarationLine : declarationLineList) {
      if (declarationLine.getTypeSelect() == DeclarationLineRepository.TYPE_END_OF_PACK) {
        declarationLine.setIsHideUnitAmounts(Boolean.FALSE);
        declarationLine.setIsShowTotal(Boolean.FALSE);
        declarationLine.setExTaxTotal(BigDecimal.ZERO);
        declarationLine.setInTaxTotal(BigDecimal.ZERO);
      }
    }
    declaration.setDeclarationLineList(declarationLineList);
  }
}
