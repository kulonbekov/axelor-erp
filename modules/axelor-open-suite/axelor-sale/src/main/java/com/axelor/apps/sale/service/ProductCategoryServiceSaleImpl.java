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

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.ProductCategory;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.ProductCategoryRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.apps.base.service.ProductCategoryServiceImpl;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProductCategoryServiceSaleImpl extends ProductCategoryServiceImpl
    implements ProductCategorySaleService {

  protected DeclarationLineRepository declarationLineRepository;

  @Inject
  public ProductCategoryServiceSaleImpl(
      ProductCategoryRepository productCategoryRepository,
      DeclarationLineRepository declarationLineRepository) {
    super(productCategoryRepository);
    this.declarationLineRepository = declarationLineRepository;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateDeclarationLines(ProductCategory productCategory) throws AxelorException {
    List<ProductCategory> impactedProductCategories =
        fetchImpactedChildrenProductCategories(productCategory);
    impactedProductCategories.add(productCategory);
    declarationLineRepository
        .all()
        .filter(
            // fetch children
            "self.product.productCategory.id IN :productCategoryIds "
                + "AND self.declaration.statusSelect != :statusCompleted "
                + "AND self.declaration.statusSelect != :statusCanceled")
        .bind(
            "productCategoryIds",
            impactedProductCategories.stream()
                .map(ProductCategory::getId)
                .collect(Collectors.toList()))
        .bind("statusCompleted", DeclarationRepository.STATUS_ORDER_COMPLETED)
        .bind("statusCanceled", DeclarationRepository.STATUS_CANCELED)
        .fetchStream()
        .filter(declarationLine -> hasDiscountBecameTooHigh(productCategory, declarationLine))
        .forEach(declarationLine -> declarationLine.setDiscountsNeedReview(true));
  }

  /**
   * Fetch impacted children product categories on a category change. To find impacted product
   * categories, we fetch children of given product category. If these children do not have a max
   * discount, they are impacted and their own children can be impacted following the same pattern.
   *
   * @param productCategory a product category
   * @return the computed list of children impacted by a max discount change in given category.
   * @throws AxelorException if there is a configuration error
   */
  protected List<ProductCategory> fetchImpactedChildrenProductCategories(
      ProductCategory productCategory) throws AxelorException {
    // security in case of code error to avoid infinite loop
    int i = 0;
    List<ProductCategory> descendantsProductCategoryList = new ArrayList<>();
    if (productCategory.getId() == null) {
      // if product category is not saved, then it cannot have children
      return descendantsProductCategoryList;
    }
    // product categories with max discounts are not be impacted
    List<ProductCategory> childrenProductCategoryList =
        fetchChildrenWitNoMaxDiscount(productCategory);
    while (!childrenProductCategoryList.isEmpty() && i < MAX_ITERATION) {
      List<ProductCategory> nextChildrenProductCategoryList = new ArrayList<>();
      for (ProductCategory childProductCategory : childrenProductCategoryList) {
        if (descendantsProductCategoryList.contains(childProductCategory)
            || childProductCategory.equals(productCategory)) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(BaseExceptionMessage.PRODUCT_CATEGORY_CHILDREN_CIRCULAR_DEPENDENCY),
              childProductCategory.getCode());
        }
        descendantsProductCategoryList.add(childProductCategory);
        nextChildrenProductCategoryList.addAll(fetchChildrenWitNoMaxDiscount(childProductCategory));
      }
      childrenProductCategoryList.clear();
      childrenProductCategoryList.addAll(nextChildrenProductCategoryList);
      nextChildrenProductCategoryList.clear();
      i++;
    }
    return descendantsProductCategoryList;
  }

  protected List<ProductCategory> fetchChildrenWitNoMaxDiscount(ProductCategory productCategory) {
    return productCategoryRepository
        .all()
        .filter("self.parentProductCategory.id = :productCategoryId AND self.maxDiscount <= 0")
        .bind("productCategoryId", productCategory.getId())
        .fetch();
  }

  protected boolean hasDiscountBecameTooHigh(
      ProductCategory productCategory, DeclarationLine declarationLine) {
    ProductCategory productCategoryIt = productCategory;
    BigDecimal maxDiscount = productCategory.getMaxDiscount();
    while (maxDiscount.signum() == 0 && productCategoryIt.getParentProductCategory() != null) {
      productCategoryIt = productCategory.getParentProductCategory();
      maxDiscount = productCategoryIt.getMaxDiscount();
    }
    if (maxDiscount.signum() == 0) {
      return false;
    }
    // compute discount percent in sale order line
    BigDecimal declarationLineDiscount;
    switch (declarationLine.getDiscountTypeSelect()) {
      case PriceListLineRepository.AMOUNT_TYPE_PERCENT:
        declarationLineDiscount = declarationLine.getDiscountAmount();
        break;
      case PriceListLineRepository.AMOUNT_TYPE_FIXED:
        declarationLineDiscount =
            declarationLine.getPrice().signum() != 0
                ? declarationLine
                    .getDiscountAmount()
                    .multiply(new BigDecimal("100"))
                    .divide(declarationLine.getPrice(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        break;
      case PriceListLineRepository.AMOUNT_TYPE_NONE:
      default:
        declarationLineDiscount = BigDecimal.ZERO;
        break;
    }
    return declarationLineDiscount.compareTo(maxDiscount) > 0;
  }
}
