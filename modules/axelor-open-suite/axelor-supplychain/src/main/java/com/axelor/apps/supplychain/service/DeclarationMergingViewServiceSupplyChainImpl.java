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

import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationMergingService;
import com.axelor.apps.sale.service.declaration.DeclarationMergingService.DeclarationMergingResult;
import com.axelor.apps.sale.service.declaration.DeclarationMergingViewServiceImpl;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.google.inject.Inject;
import java.util.List;

public class DeclarationMergingViewServiceSupplyChainImpl extends DeclarationMergingViewServiceImpl {

  protected AppSaleService appSaleService;
  protected DeclarationMergingServiceSupplyChainImpl declarationMergingSupplyChainService;

  @Inject
  public DeclarationMergingViewServiceSupplyChainImpl(
      DeclarationMergingService declarationMergingService,
      AppSaleService appSaleService,
      DeclarationMergingServiceSupplyChainImpl declarationMergingSupplyChainService) {
    super(declarationMergingService);
    this.appSaleService = appSaleService;
    this.declarationMergingSupplyChainService = declarationMergingSupplyChainService;
  }

  @Override
  public ActionViewBuilder buildConfirmView(
      DeclarationMergingResult result, String lineToMerge, List<Declaration> declarationToMerge) {
    if (!appSaleService.isApp("supplychain")) {
      return super.buildConfirmView(result, lineToMerge, declarationToMerge);
    }

    ActionViewBuilder confirmView = super.buildConfirmView(result, lineToMerge, declarationToMerge);
    if (declarationMergingSupplyChainService.getChecks(result).isExistStockLocationDiff()) {
      confirmView.context("contextLocationToCheck", Boolean.TRUE.toString());
    }
    return confirmView;
  }
}
