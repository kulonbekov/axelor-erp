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

import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.apps.supplychain.db.MrpLineType;
import com.axelor.apps.supplychain.db.repo.MrpLineTypeRepository;
import com.axelor.apps.supplychain.db.repo.MrpRepository;
import com.axelor.utils.StringTool;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MrpFilterDeclarationLineServiceImpl implements MrpFilterDeclarationLineService {

  protected StockLocationService stockLocationService;
  protected DeclarationLineRepository declarationLineRepository;
  protected MrpLineTypeService mrpLineTypeService;
  protected MrpDeclarationCheckLateSaleService mrpDeclarationCheckLateSaleService;

  @Inject
  public MrpFilterDeclarationLineServiceImpl(
      StockLocationService stockLocationService,
      DeclarationLineRepository declarationLineRepository,
      MrpLineTypeService mrpLineTypeService,
      MrpDeclarationCheckLateSaleService mrpDeclarationCheckLateSaleService) {
    this.stockLocationService = stockLocationService;
    this.declarationLineRepository = declarationLineRepository;
    this.mrpLineTypeService = mrpLineTypeService;
    this.mrpDeclarationCheckLateSaleService = mrpDeclarationCheckLateSaleService;
  }

  @Override
  public List<Long> getDeclarationLinesComplyingToMrpLineTypes(Mrp mrp) {

    List<Long> idList = new ArrayList<>();
    idList.add((long) -1);

    List<MrpLineType> declarationMrpLineTypeList =
        mrpLineTypeService.getMrpLineTypeList(
            MrpLineTypeRepository.ELEMENT_SALE_ORDER, mrp.getMrpTypeSelect());

    if ((declarationMrpLineTypeList != null && !declarationMrpLineTypeList.isEmpty())
        && mrp.getStockLocation() != null) {

      List<StockLocation> stockLocationList =
          stockLocationService.getAllLocationAndSubLocation(mrp.getStockLocation(), false).stream()
              .filter(x -> !x.getIsNotInMrp())
              .collect(Collectors.toList());

      for (MrpLineType declarationMrpLineType : declarationMrpLineTypeList) {
        idList.addAll(
            getDeclarationLinesComplyingToMrpLineType(mrp, stockLocationList, declarationMrpLineType));
        idList = idList.stream().distinct().collect(Collectors.toList());
      }
    }

    return idList;
  }

  protected List<Long> getDeclarationLinesComplyingToMrpLineType(
      Mrp mrp, List<StockLocation> stockLocationList, MrpLineType declarationMrpLineType) {

    List<Integer> statusList = StringTool.getIntegerList(declarationMrpLineType.getStatusSelect());

    String filter =
        "self.product.productTypeSelect = 'storable'"
            + " AND self.declaration IS NOT NULL"
            + " AND self.product.excludeFromMrp = false"
            + " AND self.product.stockManaged = true"
            + " AND self.deliveryState != :deliveryState"
            + " AND self.declaration.company.id = :companyId"
            + " AND self.declaration.stockLocation IN (:stockLocations)"
            + " AND (:mrpTypeSelect = :mrpTypeMrp OR self.product.productSubTypeSelect = :productSubTypeFinished)"
            + " AND self.declaration.statusSelect IN (:declarationStatusList)"
            + " AND self.deliveredQty < self.qty"
            + " AND (self.declaration.archived = false OR self.declaration.archived is null)";

    // Checking the one off sales parameter
    if (declarationMrpLineType.getIncludeOneOffSalesSelect()
        == MrpLineTypeRepository.ONE_OFF_SALES_EXCLUDED) {
      filter += "AND (self.declaration.oneoffSale IS NULL OR self.declaration.oneoffSale IS FALSE)";
    } else if (declarationMrpLineType.getIncludeOneOffSalesSelect()
        == MrpLineTypeRepository.ONE_OFF_SALES_ONLY) {
      filter += "AND self.declaration.oneoffSale IS TRUE";
    }

    List<DeclarationLine> declarationLineList =
        declarationLineRepository
            .all()
            .filter(filter)
            .bind("deliveryState", DeclarationLineRepository.DELIVERY_STATE_DELIVERED)
            .bind(
                "companyId",
                mrp.getStockLocation() != null ? mrp.getStockLocation().getCompany().getId() : -1)
            .bind("stockLocations", stockLocationList)
            .bind("mrpTypeSelect", mrp.getMrpTypeSelect())
            .bind("mrpTypeMrp", MrpRepository.MRP_TYPE_MRP)
            .bind("productSubTypeFinished", ProductRepository.PRODUCT_SUB_TYPE_FINISHED_PRODUCT)
            .bind("declarationStatusList", statusList)
            .fetch();
    return declarationLineList.stream()
        .filter(
            declarationLine ->
                mrpDeclarationCheckLateSaleService.checkLateSalesParameter(
                    declarationLine, declarationMrpLineType))
        .map(DeclarationLine::getId)
        .collect(Collectors.toList());
  }
}
