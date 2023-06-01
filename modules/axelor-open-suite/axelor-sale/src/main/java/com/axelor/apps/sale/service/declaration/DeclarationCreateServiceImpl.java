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

import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.TaxNumber;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.inject.Beans;
import com.axelor.team.db.Team;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationCreateServiceImpl implements DeclarationCreateService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected PartnerService partnerService;
  protected DeclarationRepository declarationRepo;
  protected AppSaleService appSaleService;
  protected DeclarationService declarationService;
  protected DeclarationComputeService declarationComputeService;

  @Inject
  public DeclarationCreateServiceImpl(
      PartnerService partnerService,
      DeclarationRepository declarationRepo,
      AppSaleService appSaleService,
      DeclarationService declarationService,
      DeclarationComputeService declarationComputeService) {

    this.partnerService = partnerService;
    this.declarationRepo = declarationRepo;
    this.appSaleService = appSaleService;
    this.declarationService = declarationService;
    this.declarationComputeService = declarationComputeService;
  }

  @Override
  public Declaration createDeclaration(Company company) throws AxelorException {
    Declaration declaration = new Declaration();
    declaration.setCreationDate(appSaleService.getTodayDate(company));
    if (company != null) {
      declaration.setCompany(company);
      declaration.setCurrency(company.getCurrency());
    }
    declaration.setSalespersonUser(AuthUtils.getUser());
    declaration.setTeam(declaration.getSalespersonUser().getActiveTeam());
    declaration.setStatusSelect(DeclarationRepository.STATUS_DRAFT_QUOTATION);
    declarationService.computeEndOfValidityDate(declaration);
    return declaration;
  }

  @Override
  public Declaration createDeclaration(
      User salespersonUser,
      Company company,
      Partner contactPartner,
      Currency currency,
      LocalDate estimatedShippingDate,
      String internalReference,
      String externalReference,
      PriceList priceList,
      Partner clientPartner,
      Team team,
      TaxNumber taxNumber,
      FiscalPosition fiscalPosition,
      TradingName tradingName)
      throws AxelorException {

    logger.debug(
        "Creation of a sale order: Company = {},  External reference = {}, Supplier partner = {}",
        company,
        externalReference,
        clientPartner.getFullName());

    Declaration declaration = new Declaration();
    declaration.setClientPartner(clientPartner);
    declaration.setCreationDate(appSaleService.getTodayDate(company));
    declaration.setContactPartner(contactPartner);
    declaration.setCurrency(currency);
    declaration.setExternalReference(externalReference);
    declaration.setEstimatedShippingDate(estimatedShippingDate);
    declaration.setEstimatedDeliveryDate(estimatedShippingDate);
    declaration.setTaxNumber(taxNumber);
    declaration.setFiscalPosition(fiscalPosition);

    declaration.setPrintingSettings(
        Beans.get(TradingNameService.class).getDefaultPrintingSettings(tradingName, company));

    if (salespersonUser == null) {
      salespersonUser = AuthUtils.getUser();
    }
    declaration.setSalespersonUser(salespersonUser);

    if (team == null) {
      team = salespersonUser.getActiveTeam();
    }
    declaration.setTeam(team);

    if (company == null) {
      company = salespersonUser.getActiveCompany();
    }
    declaration.setCompany(company);

    declaration.setMainInvoicingAddress(partnerService.getInvoicingAddress(clientPartner));
    declaration.setDeliveryAddress(partnerService.getDeliveryAddress(clientPartner));

    declarationService.computeAddressStr(declaration);

    if (priceList == null) {
      priceList =
          Beans.get(PartnerPriceListService.class)
              .getDefaultPriceList(clientPartner, PriceListRepository.TYPE_SALE);
    }
    declaration.setPriceList(priceList);

    declaration.setDeclarationLineList(new ArrayList<>());

    declaration.setStatusSelect(DeclarationRepository.STATUS_DRAFT_QUOTATION);

    declarationService.computeEndOfValidityDate(declaration);

    return declaration;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Declaration mergeDeclarations(
      List<Declaration> declarationList,
      Currency currency,
      Partner clientPartner,
      Company company,
      Partner contactPartner,
      PriceList priceList,
      Team team,
      TaxNumber taxNumber,
      FiscalPosition fiscalPosition)
      throws AxelorException {

    String numSeq = "";
    String externalRef = "";
    for (Declaration declarationLocal : declarationList) {
      if (!numSeq.isEmpty()) {
        numSeq += "-";
      }
      numSeq += declarationLocal.getDeclarationSeq();

      if (!externalRef.isEmpty()) {
        externalRef += "|";
      }
      if (declarationLocal.getExternalReference() != null) {
        externalRef += declarationLocal.getExternalReference();
      }
    }

    Declaration declarationMerged =
        this.createDeclaration(
            AuthUtils.getUser(),
            company,
            contactPartner,
            currency,
            null,
            numSeq,
            externalRef,
            priceList,
            clientPartner,
            team,
            taxNumber,
            fiscalPosition);

    this.attachToNewDeclaration(declarationList, declarationMerged);

    declarationComputeService.computeDeclaration(declarationMerged);

    declarationRepo.save(declarationMerged);

    this.removeOldDeclarations(declarationList);

    return declarationMerged;
  }

  // Attachment of all sale order lines to new sale order
  protected void attachToNewDeclaration(List<Declaration> declarationList, Declaration declarationMerged) {
    for (Declaration declaration : declarationList) {
      int countLine = 1;
      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
        declarationLine.setSequence(countLine * 10);
        declarationMerged.addDeclarationLineListItem(declarationLine);
        countLine++;
      }
    }
  }

  // Remove old sale orders after merge
  protected void removeOldDeclarations(List<Declaration> declarationList) {
    for (Declaration declaration : declarationList) {
      declarationRepo.remove(declaration);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Declaration createDeclaration(
      Declaration context, Currency wizardCurrency, PriceList wizardPriceList)
      throws AxelorException {
    Declaration copy = declarationRepo.copy(context, true);
    copy.setCreationDate(appSaleService.getTodayDate(context.getCompany()));
    copy.setCurrency(wizardCurrency);
    copy.setPriceList(wizardPriceList);

    declarationService.computeEndOfValidityDate(copy);

    this.updateDeclarationLineList(copy);

    declarationComputeService.computeDeclaration(copy);

    copy.setTemplate(false);
    copy.setTemplateUser(null);

    return copy;
  }

  public void updateDeclarationLineList(Declaration declaration) throws AxelorException {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (declarationLineList != null) {
      DeclarationLineService declarationLineService = Beans.get(DeclarationLineService.class);
      for (DeclarationLine declarationLine : declarationLineList) {
        if (declarationLine.getProduct() != null) {
          declarationLineService.resetPrice(declarationLine);
          declarationLineService.fillPrice(declarationLine, declaration);
          declarationLineService.computeValues(declaration, declarationLine);
        }
      }
    }
  }

  @Override
  @Transactional
  public Declaration createTemplate(Declaration context) {
    Declaration copy = declarationRepo.copy(context, true);
    copy.setTemplate(true);
    copy.setTemplateUser(AuthUtils.getUser());
    return copy;
  }
}
