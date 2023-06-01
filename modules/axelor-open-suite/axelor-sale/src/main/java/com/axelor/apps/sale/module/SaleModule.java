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
package com.axelor.apps.sale.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.service.PartnerServiceImpl;
import com.axelor.apps.base.service.ProductCategoryServiceImpl;
import com.axelor.apps.crm.db.repo.OpportunityManagementRepository;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.AdvancePaymentRepository;
import com.axelor.apps.sale.db.repo.AdvancePaymentSaleRepository;
import com.axelor.apps.sale.db.repo.ConfiguratorCreatorRepository;
import com.axelor.apps.sale.db.repo.ConfiguratorCreatorSaleRepository;
import com.axelor.apps.sale.db.repo.OpportunitySaleRepository;
import com.axelor.apps.sale.db.repo.SaleBatchRepository;
import com.axelor.apps.sale.db.repo.SaleBatchSaleRepository;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationManagementRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.AddressServiceSaleImpl;
import com.axelor.apps.sale.service.AdvancePaymentService;
import com.axelor.apps.sale.service.AdvancePaymentServiceImpl;
import com.axelor.apps.sale.service.PackLineService;
import com.axelor.apps.sale.service.PackLineServiceImpl;
import com.axelor.apps.sale.service.PartnerSaleService;
import com.axelor.apps.sale.service.PartnerSaleServiceImpl;
import com.axelor.apps.sale.service.ProductCategorySaleService;
import com.axelor.apps.sale.service.ProductCategoryServiceSaleImpl;
import com.axelor.apps.sale.service.DeclarationDomainService;
import com.axelor.apps.sale.service.DeclarationDomainServiceImpl;
import com.axelor.apps.sale.service.DeclarationLineSaleRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.app.AppSaleServiceImpl;
import com.axelor.apps.sale.service.config.SaleConfigService;
import com.axelor.apps.sale.service.config.SaleConfigServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorCreatorImportService;
import com.axelor.apps.sale.service.configurator.ConfiguratorCreatorImportServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorCreatorService;
import com.axelor.apps.sale.service.configurator.ConfiguratorCreatorServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorFormulaService;
import com.axelor.apps.sale.service.configurator.ConfiguratorFormulaServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorMetaJsonFieldService;
import com.axelor.apps.sale.service.configurator.ConfiguratorMetaJsonFieldServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorService;
import com.axelor.apps.sale.service.configurator.ConfiguratorServiceImpl;
import com.axelor.apps.sale.service.declaration.OpportunityDeclarationService;
import com.axelor.apps.sale.service.declaration.OpportunityDeclarationServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationComputeService;
import com.axelor.apps.sale.service.declaration.DeclarationComputeServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationCreateService;
import com.axelor.apps.sale.service.declaration.DeclarationCreateServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.sale.service.declaration.DeclarationLineServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationMarginService;
import com.axelor.apps.sale.service.declaration.DeclarationMarginServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationMergingService;
import com.axelor.apps.sale.service.declaration.DeclarationMergingServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationMergingViewService;
import com.axelor.apps.sale.service.declaration.DeclarationMergingViewServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.apps.sale.service.declaration.DeclarationServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationWorkflowService;
import com.axelor.apps.sale.service.declaration.DeclarationWorkflowServiceImpl;
import com.axelor.apps.sale.service.declaration.print.DeclarationPrintService;
import com.axelor.apps.sale.service.declaration.print.DeclarationPrintServiceImpl;

public class SaleModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AddressServiceSaleImpl.class);
    bind(PartnerServiceImpl.class).to(PartnerSaleServiceImpl.class);
    bind(DeclarationService.class).to(DeclarationServiceImpl.class);
    bind(DeclarationLineService.class).to(DeclarationLineServiceImpl.class);
    bind(DeclarationRepository.class).to(DeclarationManagementRepository.class);
    bind(DeclarationWorkflowService.class).to(DeclarationWorkflowServiceImpl.class);
    bind(DeclarationMarginService.class).to(DeclarationMarginServiceImpl.class);
    bind(DeclarationCreateService.class).to(DeclarationCreateServiceImpl.class);
    bind(DeclarationComputeService.class).to(DeclarationComputeServiceImpl.class);
    bind(OpportunityDeclarationService.class).to(OpportunityDeclarationServiceImpl.class);
    bind(AdvancePaymentService.class).to(AdvancePaymentServiceImpl.class);
    bind(AppSaleService.class).to(AppSaleServiceImpl.class);
    bind(SaleConfigService.class).to(SaleConfigServiceImpl.class);
    bind(SaleBatchRepository.class).to(SaleBatchSaleRepository.class);
    PartnerAddressRepository.modelPartnerFieldMap.put(Declaration.class.getName(), "clientPartner");
    bind(AdvancePaymentRepository.class).to(AdvancePaymentSaleRepository.class);
    bind(ConfiguratorCreatorService.class).to(ConfiguratorCreatorServiceImpl.class);
    bind(ConfiguratorService.class).to(ConfiguratorServiceImpl.class);
    bind(ConfiguratorFormulaService.class).to(ConfiguratorFormulaServiceImpl.class);
    bind(ConfiguratorCreatorImportService.class).to(ConfiguratorCreatorImportServiceImpl.class);
    bind(DeclarationPrintService.class).to(DeclarationPrintServiceImpl.class);
    bind(OpportunityManagementRepository.class).to(OpportunitySaleRepository.class);
    bind(PartnerSaleService.class).to(PartnerSaleServiceImpl.class);
    bind(PackLineService.class).to(PackLineServiceImpl.class);
    bind(ProductCategorySaleService.class).to(ProductCategoryServiceSaleImpl.class);
    bind(ProductCategoryServiceImpl.class).to(ProductCategoryServiceSaleImpl.class);
    bind(DeclarationLineRepository.class).to(DeclarationLineSaleRepository.class);
    bind(ConfiguratorCreatorRepository.class).to(ConfiguratorCreatorSaleRepository.class);
    bind(ConfiguratorMetaJsonFieldService.class).to(ConfiguratorMetaJsonFieldServiceImpl.class);
    bind(DeclarationDomainService.class).to(DeclarationDomainServiceImpl.class);
    bind(DeclarationMergingViewService.class).to(DeclarationMergingViewServiceImpl.class);
    bind(DeclarationMergingService.class).to(DeclarationMergingServiceImpl.class);
  }
}
