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

import static com.axelor.apps.base.service.administration.AbstractBatch.FETCH_LIMIT;
import static com.axelor.utils.date.DateTool.toDate;
import static com.axelor.utils.date.DateTool.toLocalDateT;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.ABCAnalysis;
import com.axelor.apps.base.db.ABCAnalysisLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ABCAnalysisClassRepository;
import com.axelor.apps.base.db.repo.ABCAnalysisLineRepository;
import com.axelor.apps.base.db.repo.ABCAnalysisRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.ABCAnalysisServiceImpl;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class ABCAnalysisServiceSaleImpl extends ABCAnalysisServiceImpl {
  protected DeclarationLineRepository declarationLineRepository;

  private static final String SELLABLE_TRUE = " AND self.sellable = TRUE";

  @Inject
  public ABCAnalysisServiceSaleImpl(
      ABCAnalysisLineRepository abcAnalysisLineRepository,
      UnitConversionService unitConversionService,
      ABCAnalysisRepository abcAnalysisRepository,
      ProductRepository productRepository,
      DeclarationLineRepository declarationLineRepository,
      ABCAnalysisClassRepository abcAnalysisClassRepository,
      SequenceService sequenceService) {
    super(
        abcAnalysisLineRepository,
        unitConversionService,
        abcAnalysisRepository,
        productRepository,
        abcAnalysisClassRepository,
        sequenceService);
    this.declarationLineRepository = declarationLineRepository;
  }

  @Override
  protected Optional<ABCAnalysisLine> createABCAnalysisLine(
      ABCAnalysis abcAnalysis, Product product) throws AxelorException {
    ABCAnalysisLine abcAnalysisLine = null;
    BigDecimal productQty = BigDecimal.ZERO;
    BigDecimal productWorth = BigDecimal.ZERO;
    List<DeclarationLine> declarationLineList;
    int offset = 0;

    Query<DeclarationLine> declarationLineQuery =
        declarationLineRepository
            .all()
            .filter(
                "(self.declaration.statusSelect = :statusConfirmed OR self.declaration.statusSelect = :statusCompleted) AND self.declaration.confirmationDateTime >= :startDate AND self.declaration.confirmationDateTime <= :endDate AND self.product.id = :productId")
            .bind("statusConfirmed", DeclarationRepository.STATUS_ORDER_CONFIRMED)
            .bind("statusCompleted", DeclarationRepository.STATUS_ORDER_COMPLETED)
            .bind("startDate", toLocalDateT(toDate(abcAnalysis.getStartDate())))
            .bind(
                "endDate",
                toLocalDateT(toDate(abcAnalysis.getEndDate()))
                    .withHour(23)
                    .withMinute(59)
                    .withSecond(59))
            .bind("productId", product.getId())
            .order("id");

    while (!(declarationLineList = declarationLineQuery.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      offset += declarationLineList.size();
      abcAnalysis = abcAnalysisRepository.find(abcAnalysis.getId());

      if (abcAnalysisLine == null) {
        abcAnalysisLine = super.createABCAnalysisLine(abcAnalysis, product).get();
      }

      for (DeclarationLine declarationLine : declarationLineList) {
        BigDecimal convertedQty =
            unitConversionService.convert(
                declarationLine.getUnit(), product.getUnit(), declarationLine.getQty(), 5, product);
        productQty = productQty.add(convertedQty);
        productWorth = productWorth.add(declarationLine.getCompanyExTaxTotal());
      }

      super.incTotalQty(productQty);
      super.incTotalWorth(productWorth);

      JPA.clear();
    }

    if (abcAnalysisLine != null) {
      setQtyWorth(
          abcAnalysisLineRepository.find(abcAnalysisLine.getId()), productQty, productWorth);
    }

    return Optional.ofNullable(abcAnalysisLine);
  }

  @Override
  protected String getProductCategoryQuery() {
    return super.getProductCategoryQuery() + SELLABLE_TRUE;
  }

  @Override
  protected String getProductFamilyQuery() {
    return super.getProductFamilyQuery() + SELLABLE_TRUE;
  }
}
