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
package com.axelor.apps.production.service.productionorder;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ProductionOrder;
import com.axelor.apps.production.db.repo.ProductionOrderRepository;
import com.axelor.apps.production.exceptions.ProductionExceptionMessage;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.production.service.manuforder.ManufOrderService.ManufOrderOriginTypeProduction;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProductionOrderDeclarationServiceImpl implements ProductionOrderDeclarationService {

  protected UnitConversionService unitConversionService;
  protected ProductionOrderService productionOrderService;
  protected ProductionOrderRepository productionOrderRepo;
  protected AppProductionService appProductionService;

  @Inject
  public ProductionOrderDeclarationServiceImpl(
      UnitConversionService unitConversionService,
      ProductionOrderService productionOrderService,
      ProductionOrderRepository productionOrderRepo,
      AppProductionService appProductionService) {

    this.unitConversionService = unitConversionService;
    this.productionOrderService = productionOrderService;
    this.productionOrderRepo = productionOrderRepo;
    this.appProductionService = appProductionService;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class})
  public List<Long> generateProductionOrder(Declaration declaration) throws AxelorException {

    boolean oneProdOrderPerSO = appProductionService.getAppProduction().getOneProdOrderPerSO();

    List<Long> productionOrderIdList = new ArrayList<>();
    if (declaration.getDeclarationLineList() == null) {
      return productionOrderIdList;
    }

    ProductionOrder productionOrder = null;
    for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {

      if (productionOrder == null || !oneProdOrderPerSO) {
        productionOrder = this.createProductionOrder(declaration);
      }

      productionOrder = this.generateManufOrders(productionOrder, declarationLine);

      if (productionOrder != null && !productionOrderIdList.contains(productionOrder.getId())) {
        productionOrderIdList.add(productionOrder.getId());
      }
    }

    return productionOrderIdList;
  }

  protected ProductionOrder createProductionOrder(Declaration declaration) throws AxelorException {

    return productionOrderService.createProductionOrder(declaration);
  }

  @Override
  public ProductionOrder generateManufOrders(
      ProductionOrder productionOrder, DeclarationLine declarationLine) throws AxelorException {

    Product product = declarationLine.getProduct();

    if (declarationLine.getSaleSupplySelect() == ProductRepository.SALE_SUPPLY_PRODUCE
        && product != null
        && product.getProductTypeSelect().equals(ProductRepository.PRODUCT_TYPE_STORABLE)) {

      BillOfMaterial billOfMaterial = declarationLine.getBillOfMaterial();

      if (billOfMaterial == null) {
        billOfMaterial = product.getDefaultBillOfMaterial();
      }

      if (billOfMaterial == null && product.getParentProduct() != null) {
        billOfMaterial = product.getParentProduct().getDefaultBillOfMaterial();
      }

      if (billOfMaterial == null) {
        throw new AxelorException(
            declarationLine,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(ProductionExceptionMessage.PRODUCTION_ORDER_SALES_ORDER_NO_BOM),
            product.getName(),
            product.getCode());
      }

      if (billOfMaterial.getProdProcess() == null) {
        return null;
      }

      Unit unit = declarationLine.getProduct().getUnit();
      BigDecimal qty = declarationLine.getQty();
      if (unit != null && !unit.equals(declarationLine.getUnit())) {
        qty =
            unitConversionService.convert(
                declarationLine.getUnit(), unit, qty, qty.scale(), declarationLine.getProduct());
      }

      return generateManufOrders(
          productionOrder,
          billOfMaterial,
          qty,
          LocalDateTime.now(),
          declarationLine.getDeclaration(),
          declarationLine);
    }

    return null;
  }

  /**
   * Loop through bill of materials components to generate manufacturing order for given sale order
   * line and all of its sub manuf order needed to get components for parent manufacturing order.
   *
   * @param productionOrder Initialized production order with no manufacturing order.
   * @param billOfMaterial the bill of material of the parent manufacturing order
   * @param qtyRequested the quantity requested of the parent manufacturing order.
   * @param startDate startDate of creation
   * @param declaration a sale order
   * @return the updated production order with all generated manufacturing orders.
   * @throws AxelorException
   */
  protected ProductionOrder generateManufOrders(
      ProductionOrder productionOrder,
      BillOfMaterial billOfMaterial,
      BigDecimal qtyRequested,
      LocalDateTime startDate,
      Declaration declaration,
      DeclarationLine declarationLine)
      throws AxelorException {

    List<BillOfMaterial> childBomList = new ArrayList<>();
    childBomList.add(billOfMaterial);
    // prevent infinite loop
    int depth = 0;
    while (!childBomList.isEmpty()) {
      if (depth >= 100) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(ProductionExceptionMessage.CHILD_BOM_TOO_MANY_ITERATION));
      }
      List<BillOfMaterial> tempChildBomList = new ArrayList<>();
      for (BillOfMaterial childBom : childBomList) {
        productionOrder =
            productionOrderService.addManufOrder(
                productionOrder,
                childBom.getProduct(),
                childBom,
                qtyRequested.multiply(childBom.getQty()),
                startDate,
                null,
                declaration,
                declarationLine,
                ManufOrderOriginTypeProduction.ORIGIN_TYPE_SALE_ORDER);
        tempChildBomList.addAll(
            childBom.getBillOfMaterialSet().stream()
                .filter(BillOfMaterial::getDefineSubBillOfMaterial)
                .collect(Collectors.toList()));
      }
      childBomList.clear();
      childBomList.addAll(tempChildBomList);
      tempChildBomList.clear();
      depth++;
    }
    return productionOrder;
  }
}
