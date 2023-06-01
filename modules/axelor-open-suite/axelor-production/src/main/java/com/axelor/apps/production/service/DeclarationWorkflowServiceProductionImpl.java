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
package com.axelor.apps.production.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.service.app.AppCrmService;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.production.service.productionorder.ProductionOrderDeclarationService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.config.SaleConfigService;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.supplychain.service.AccountingSituationSupplychainService;
import com.axelor.apps.supplychain.service.PartnerSupplychainService;
import com.axelor.apps.supplychain.service.DeclarationCheckAnalyticService;
import com.axelor.apps.supplychain.service.DeclarationPurchaseService;
import com.axelor.apps.supplychain.service.DeclarationStockService;
import com.axelor.apps.supplychain.service.DeclarationWorkflowServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class DeclarationWorkflowServiceProductionImpl
    extends DeclarationWorkflowServiceSupplychainImpl {

  protected ProductionOrderDeclarationService productionOrderDeclarationService;
  protected AppProductionService appProductionService;

  @Inject
  public DeclarationWorkflowServiceProductionImpl(
      SequenceService sequenceService,
      PartnerRepository partnerRepo,
      DeclarationRepository declarationRepo,
      AppSaleService appSaleService,
      AppCrmService appCrmService,
      UserService userService,
      DeclarationLineService declarationLineService,
      DeclarationStockService declarationStockService,
      DeclarationPurchaseService declarationPurchaseService,
      AppSupplychainService appSupplychainService,
      AccountingSituationSupplychainService accountingSituationSupplychainService,
      PartnerSupplychainService partnerSupplychainService,
      SaleConfigService saleConfigService,
      DeclarationCheckAnalyticService declarationCheckAnalyticService,
      ProductionOrderDeclarationService productionOrderDeclarationService,
      AppProductionService appProductionService) {
    super(
        sequenceService,
        partnerRepo,
        declarationRepo,
        appSaleService,
        appCrmService,
        userService,
        declarationLineService,
        declarationStockService,
        declarationPurchaseService,
        appSupplychainService,
        accountingSituationSupplychainService,
        partnerSupplychainService,
        saleConfigService,
        declarationCheckAnalyticService);
    this.productionOrderDeclarationService = productionOrderDeclarationService;
    this.appProductionService = appProductionService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirmDeclaration(Declaration declaration) throws AxelorException {
    super.confirmDeclaration(declaration);

    if (appProductionService.isApp("production")
        && appProductionService.getAppProduction().getProductionOrderGenerationAuto()) {
      productionOrderDeclarationService.generateProductionOrder(declaration);
    }
  }
}
