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
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.exceptions.BaseExceptionMessage;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.exception.SaleExceptionMessage;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class OpportunityDeclarationServiceImpl implements OpportunityDeclarationService {

  protected DeclarationCreateService declarationCreateService;

  protected DeclarationRepository declarationRepo;

  protected AppBaseService appBaseService;

  @Inject
  public OpportunityDeclarationServiceImpl(
      DeclarationCreateService declarationCreateService,
      DeclarationRepository declarationRepo,
      AppBaseService appBaseService) {
    this.declarationCreateService = declarationCreateService;
    this.declarationRepo = declarationRepo;
    this.appBaseService = appBaseService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Declaration createDeclarationFromOpportunity(Opportunity opportunity) throws AxelorException {
    if (opportunity.getPartner() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(SaleExceptionMessage.OPPORTUNITY_PARTNER_MISSING),
          I18n.get(BaseExceptionMessage.EXCEPTION),
          opportunity.getName());
    }

    Currency currency = null;
    Company company = opportunity.getCompany();
    if (opportunity.getCurrency() != null) {
      currency = opportunity.getCurrency();
    } else if (opportunity.getPartner() != null && opportunity.getPartner().getCurrency() != null) {
      currency = opportunity.getPartner().getCurrency();
    } else if (company != null) {
      currency = company.getCurrency();
    }

    Declaration declaration = createDeclaration(opportunity, currency);

    opportunity.addDeclarationListItem(declaration);

    declaration.setTradingName(opportunity.getTradingName());

    declarationRepo.save(declaration);

    return declaration;
  }

  protected Declaration createDeclaration(Opportunity opportunity, Currency currency)
      throws AxelorException {
    return declarationCreateService.createDeclaration(
        opportunity.getUser(),
        opportunity.getCompany(),
        null,
        currency,
        null,
        opportunity.getName(),
        null,
        Beans.get(PartnerPriceListService.class)
            .getDefaultPriceList(opportunity.getPartner(), PriceListRepository.TYPE_SALE),
        opportunity.getPartner(),
        opportunity.getTeam(),
        null,
        opportunity.getPartner().getFiscalPosition(),
        opportunity.getTradingName());
  }
}
