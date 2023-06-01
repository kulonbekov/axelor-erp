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
package com.axelor.apps.businessproject.service;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.service.analytic.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductMultipleQtyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.pricing.PricingService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationMarginService;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.apps.supplychain.service.SaleInvoicingStateService;
import com.axelor.apps.supplychain.service.DeclarationLineServiceSupplyChainImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;

public class DeclarationLineProjectServiceImpl extends DeclarationLineServiceSupplyChainImpl
    implements DeclarationLineProjectService {

  @Inject
  public DeclarationLineProjectServiceImpl(
      CurrencyService currencyService,
      PriceListService priceListService,
      ProductMultipleQtyService productMultipleQtyService,
      AppBaseService appBaseService,
      AppSaleService appSaleService,
      AccountManagementService accountManagementService,
      DeclarationLineRepository declarationLineRepo,
      DeclarationService declarationService,
      AppAccountService appAccountService,
      AnalyticMoveLineService analyticMoveLineService,
      AppSupplychainService appSupplychainService,
      AccountConfigService accountConfigService,
      PricingService pricingService,
      TaxService taxService,
      DeclarationMarginService declarationMarginService,
      InvoiceLineRepository invoiceLineRepository,
      SaleInvoicingStateService saleInvoicingStateService) {
    super(
        currencyService,
        priceListService,
        productMultipleQtyService,
        appBaseService,
        appSaleService,
        accountManagementService,
        declarationLineRepo,
        declarationService,
        appAccountService,
        analyticMoveLineService,
        appSupplychainService,
        accountConfigService,
        pricingService,
        taxService,
        declarationMarginService,
        invoiceLineRepository,
        saleInvoicingStateService);
  }

  @Transactional
  @Override
  public void setProject(List<Long> declarationLineIds, Project project) {

    if (declarationLineIds != null) {

      List<DeclarationLine> declarationLineList =
          declarationLineRepo.all().filter("self.id in ?1", declarationLineIds).fetch();

      for (DeclarationLine line : declarationLineList) {
        line.setProject(project);
        declarationLineRepo.save(line);
      }
    }
  }

  @Override
  public DeclarationLine createAnalyticDistributionWithTemplate(DeclarationLine declarationLine) {

    DeclarationLine soLine = super.createAnalyticDistributionWithTemplate(declarationLine);
    List<AnalyticMoveLine> analyticMoveLineList = soLine.getAnalyticMoveLineList();

    if (soLine.getProject() != null && analyticMoveLineList != null) {
      analyticMoveLineList.forEach(analyticLine -> analyticLine.setProject(soLine.getProject()));
    }
    return soLine;
  }

  @Override
  public DeclarationLine updateAnalyticDistributionWithProject(DeclarationLine declarationLine) {
    for (AnalyticMoveLine analyticMoveLine : declarationLine.getAnalyticMoveLineList()) {
      analyticMoveLine.setProject(declarationLine.getProject());
    }
    return declarationLine;
  }
}
