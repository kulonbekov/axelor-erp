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

import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.TaxNumber;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationComputeService;
import com.axelor.apps.sale.service.declaration.DeclarationCreateServiceImpl;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.apps.stock.db.Incoterm;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.team.db.Team;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationCreateServiceSupplychainImpl extends DeclarationCreateServiceImpl {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AccountConfigService accountConfigService;
  protected DeclarationRepository declarationRepository;
  protected AppBaseService appBaseService;
  protected DeclarationSupplychainService declarationSupplychainService;

  @Inject
  public DeclarationCreateServiceSupplychainImpl(
      PartnerService partnerService,
      DeclarationRepository declarationRepo,
      AppSaleService appSaleService,
      AppBaseService appBaseService,
      DeclarationService declarationService,
      DeclarationComputeService declarationComputeService,
      AccountConfigService accountConfigService,
      DeclarationRepository declarationRepository,
      DeclarationSupplychainService declarationSupplychainService) {

    super(partnerService, declarationRepo, appSaleService, declarationService, declarationComputeService);

    this.accountConfigService = accountConfigService;
    this.declarationRepository = declarationRepository;
    this.appBaseService = appBaseService;
    this.declarationSupplychainService = declarationSupplychainService;
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

    if (!appSaleService.isApp("supplychain")) {
      return super.createDeclaration(
          salespersonUser,
          company,
          contactPartner,
          currency,
          estimatedShippingDate,
          internalReference,
          externalReference,
          priceList,
          clientPartner,
          team,
          taxNumber,
          fiscalPosition,
          tradingName);
    }
    return createDeclaration(
        salespersonUser,
        company,
        contactPartner,
        currency,
        estimatedShippingDate,
        internalReference,
        externalReference,
        null,
        priceList,
        clientPartner,
        team,
        taxNumber,
        fiscalPosition,
        tradingName,
        null,
        null,
        null);
  }

  public Declaration createDeclaration(
      User salespersonUser,
      Company company,
      Partner contactPartner,
      Currency currency,
      LocalDate estimatedShippingDate,
      String internalReference,
      String externalReference,
      StockLocation stockLocation,
      PriceList priceList,
      Partner clientPartner,
      Team team,
      TaxNumber taxNumber,
      FiscalPosition fiscalPosition)
      throws AxelorException {

    return createDeclaration(
        salespersonUser,
        company,
        contactPartner,
        currency,
        estimatedShippingDate,
        internalReference,
        externalReference,
        stockLocation,
        priceList,
        clientPartner,
        team,
        taxNumber,
        fiscalPosition,
        null,
        null,
        null,
        null);
  }

  public Declaration createDeclaration(
      User salespersonUser,
      Company company,
      Partner contactPartner,
      Currency currency,
      LocalDate estimatedShippingDate,
      String internalReference,
      String externalReference,
      StockLocation stockLocation,
      PriceList priceList,
      Partner clientPartner,
      Team team,
      TaxNumber taxNumber,
      FiscalPosition fiscalPosition,
      TradingName tradingName,
      Incoterm incoterm,
      Partner invoicedPartner,
      Partner deliveredPartner)
      throws AxelorException {

    logger.debug(
        "Creation of a sale order : Company = {},  External reference = {}, Customer = {}",
        company.getName(),
        externalReference,
        clientPartner.getFullName());

    Declaration declaration =
        super.createDeclaration(
            salespersonUser,
            company,
            contactPartner,
            currency,
            estimatedShippingDate,
            internalReference,
            externalReference,
            priceList,
            clientPartner,
            team,
            taxNumber,
            fiscalPosition,
            tradingName);

    if (stockLocation == null) {
      stockLocation = declarationSupplychainService.getStockLocation(clientPartner, company);
    }

    declaration.setStockLocation(stockLocation);

    declaration.setPaymentMode(clientPartner.getInPaymentMode());
    declaration.setPaymentCondition(clientPartner.getPaymentCondition());
    declaration.setIncoterm(incoterm);
    declaration.setInvoicedPartner(invoicedPartner);
    declaration.setDeliveredPartner(deliveredPartner);

    if (declaration.getPaymentMode() == null) {
      declaration.setPaymentMode(
          this.accountConfigService.getAccountConfig(company).getInPaymentMode());
    }

    if (declaration.getPaymentCondition() == null) {
      declaration.setPaymentCondition(
          this.accountConfigService.getAccountConfig(company).getDefPaymentCondition());
    }

    declaration.setShipmentMode(clientPartner.getShipmentMode());
    declaration.setFreightCarrierMode(clientPartner.getFreightCarrierMode());

    return declaration;
  }

  @Transactional(rollbackOn = {Exception.class})
  public Declaration mergeDeclarations(
      List<Declaration> declarationList,
      Currency currency,
      Partner clientPartner,
      Company company,
      StockLocation stockLocation,
      Partner contactPartner,
      PriceList priceList,
      Team team,
      TaxNumber taxNumber,
      FiscalPosition fiscalPosition,
      Incoterm incoterm,
      Partner invoicedPartner,
      Partner deliveredPartner)
      throws AxelorException {

    StringBuilder numSeq = new StringBuilder();
    StringBuilder externalRef = new StringBuilder();
    for (Declaration declarationLocal : declarationList) {
      if (numSeq.length() > 0) {
        numSeq.append("-");
      }
      numSeq.append(declarationLocal.getDeclarationSeq());

      if (externalRef.length() > 0) {
        externalRef.append("|");
      }
      if (declarationLocal.getExternalReference() != null) {
        externalRef.append(declarationLocal.getExternalReference());
      }
    }

    Declaration declarationMerged =
        this.createDeclaration(
            AuthUtils.getUser(),
            company,
            contactPartner,
            currency,
            null,
            numSeq.toString(),
            externalRef.toString(),
            stockLocation,
            priceList,
            clientPartner,
            team,
            taxNumber,
            fiscalPosition,
            null,
            incoterm,
            invoicedPartner,
            deliveredPartner);
    super.attachToNewDeclaration(declarationList, declarationMerged);

    declarationComputeService.computeDeclaration(declarationMerged);

    declarationRepository.save(declarationMerged);

    super.removeOldDeclarations(declarationList);

    return declarationMerged;
  }
}
