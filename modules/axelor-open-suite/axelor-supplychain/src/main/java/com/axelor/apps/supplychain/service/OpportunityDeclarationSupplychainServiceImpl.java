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

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.declaration.OpportunityDeclarationServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationCreateService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class OpportunityDeclarationSupplychainServiceImpl extends OpportunityDeclarationServiceImpl {

  @Inject
  public OpportunityDeclarationSupplychainServiceImpl(
      DeclarationCreateService declarationCreateService,
      DeclarationRepository declarationRepo,
      AppBaseService appBaseService) {
    super(declarationCreateService, declarationRepo, appBaseService);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Declaration createDeclarationFromOpportunity(Opportunity opportunity) throws AxelorException {
    Declaration declaration = super.createDeclarationFromOpportunity(opportunity);

    // Adding supplychain behaviour
    // Set default invoiced and delivered partners and address in case of partner delegations
    if (Beans.get(AppSupplychainService.class).getAppSupplychain().getActivatePartnerRelations()) {
      Beans.get(DeclarationSupplychainService.class)
          .setDefaultInvoicedAndDeliveredPartnersAndAddresses(declaration);
    }

    declarationRepo.save(declaration);

    return declaration;
  }
}
