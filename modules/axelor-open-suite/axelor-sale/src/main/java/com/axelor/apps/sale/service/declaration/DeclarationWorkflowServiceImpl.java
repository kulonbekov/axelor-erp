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

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Blocking;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.crm.service.app.AppCrmService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.exception.BlockedDeclarationException;
import com.axelor.apps.sale.exception.SaleExceptionMessage;
import com.axelor.apps.sale.report.IReport;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.db.JPA;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Query;

public class DeclarationWorkflowServiceImpl implements DeclarationWorkflowService {

  protected SequenceService sequenceService;
  protected PartnerRepository partnerRepo;
  protected DeclarationRepository declarationRepo;
  protected AppSaleService appSaleService;
  protected AppCrmService appCrmService;
  protected UserService userService;
  protected DeclarationLineService declarationLineService;

  @Inject
  public DeclarationWorkflowServiceImpl(
      SequenceService sequenceService,
      PartnerRepository partnerRepo,
      DeclarationRepository declarationRepo,
      AppSaleService appSaleService,
      AppCrmService appCrmService,
      UserService userService,
      DeclarationLineService declarationLineService) {

    this.sequenceService = sequenceService;
    this.partnerRepo = partnerRepo;
    this.declarationRepo = declarationRepo;
    this.appSaleService = appSaleService;
    this.appCrmService = appCrmService;
    this.userService = userService;
    this.declarationLineService = declarationLineService;
  }

  @Override
  @Transactional
  public Partner validateCustomer(Declaration declaration) {

    Partner clientPartner = partnerRepo.find(declaration.getClientPartner().getId());
    clientPartner.setIsCustomer(true);
    clientPartner.setIsProspect(false);

    return partnerRepo.save(clientPartner);
  }

  @Override
  public String getSequence(Company company) throws AxelorException {

    String seq =
        sequenceService.getSequenceNumber(
            SequenceRepository.SALES_ORDER, company, Declaration.class, "declarationSeq");
    if (seq == null) {
      throw new AxelorException(
          company,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(SaleExceptionMessage.SALES_ORDER_1),
          company.getName());
    }
    return seq;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelDeclaration(
      Declaration declaration, CancelReason cancelReason, String cancelReasonStr)
      throws AxelorException {

    List<Integer> authorizedStatus = new ArrayList<>();
    authorizedStatus.add(DeclarationRepository.STATUS_DRAFT_QUOTATION);
    authorizedStatus.add(DeclarationRepository.STATUS_FINALIZED_QUOTATION);
    if (declaration.getStatusSelect() == null
        || !authorizedStatus.contains(declaration.getStatusSelect())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SaleExceptionMessage.SALE_ORDER_CANCEL_WRONG_STATUS));
    }

    Query q =
        JPA.em()
            .createQuery(
                "select count(*) FROM Declaration as self WHERE self.statusSelect in (?1 , ?2) AND self.clientPartner = ?3 ");
    q.setParameter(1, DeclarationRepository.STATUS_ORDER_CONFIRMED);
    q.setParameter(2, DeclarationRepository.STATUS_ORDER_COMPLETED);
    q.setParameter(3, declaration.getClientPartner());
    if ((long) q.getSingleResult() == 0) {
      declaration.getClientPartner().setIsCustomer(false);
      declaration.getClientPartner().setIsProspect(true);
    }
    declaration.setStatusSelect(DeclarationRepository.STATUS_CANCELED);
    declaration.setCancelReason(cancelReason);
    if (Strings.isNullOrEmpty(cancelReasonStr)) {
      declaration.setCancelReasonStr(cancelReason.getName());
    } else {
      declaration.setCancelReasonStr(cancelReasonStr);
    }
    declarationRepo.save(declaration);
  }

  @Override
  @Transactional(
      rollbackOn = {Exception.class},
      ignore = {BlockedDeclarationException.class})
  public void finalizeQuotation(Declaration declaration) throws AxelorException {

    if (declaration.getStatusSelect() == null
        || declaration.getStatusSelect() != DeclarationRepository.STATUS_DRAFT_QUOTATION) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SaleExceptionMessage.SALE_ORDER_FINALIZE_QUOTATION_WRONG_STATUS));
    }

    Partner partner = declaration.getClientPartner();

    checkDeclarationBeforeFinalization(declaration);

    Blocking blocking =
        Beans.get(BlockingService.class)
            .getBlocking(partner, declaration.getCompany(), BlockingRepository.SALE_BLOCKING);

    if (blocking != null) {
      declaration.setBlockedOnCustCreditExceed(true);
      if (!declaration.getManualUnblock()) {
        declarationRepo.save(declaration);
        String reason =
            blocking.getBlockingReason() != null ? blocking.getBlockingReason().getName() : "";
        throw new BlockedDeclarationException(
            partner, I18n.get("Client is sale blocked:") + " " + reason);
      }
    }

    if (declaration.getVersionNumber() == 1
        && sequenceService.isEmptyOrDraftSequenceNumber(declaration.getDeclarationSeq())) {
      declaration.setDeclarationSeq(this.getSequence(declaration.getCompany()));
    }

    declaration.setStatusSelect(DeclarationRepository.STATUS_FINALIZED_QUOTATION);
    if (appSaleService.getAppSale().getPrintingOnSOFinalization()) {
      this.saveDeclarationPDFAsAttachment(declaration);
    }

    Opportunity opportunity = declaration.getOpportunity();
    if (opportunity != null) {
      opportunity.setOpportunityStatus(appCrmService.getSalesPropositionStatus());
    }

    declarationRepo.save(declaration);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirmDeclaration(Declaration declaration) throws AxelorException {
    List<Integer> authorizedStatus = new ArrayList<>();
    authorizedStatus.add(DeclarationRepository.STATUS_FINALIZED_QUOTATION);
    authorizedStatus.add(DeclarationRepository.STATUS_ORDER_COMPLETED);
    if (declaration.getStatusSelect() == null
        || !authorizedStatus.contains(declaration.getStatusSelect())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SaleExceptionMessage.SALE_ORDER_CONFIRM_WRONG_STATUS));
    }

    declaration.setStatusSelect(DeclarationRepository.STATUS_ORDER_CONFIRMED);
    declaration.setConfirmationDateTime(appSaleService.getTodayDateTime().toLocalDateTime());
    declaration.setConfirmedByUser(userService.getUser());

    this.validateCustomer(declaration);

    if (appSaleService.getAppSale().getCloseOpportunityUponDeclarationConfirmation()) {
      Opportunity opportunity = declaration.getOpportunity();
      if (opportunity != null) {
        opportunity.setOpportunityStatus(appCrmService.getClosedWinOpportunityStatus());
      }
    }

    declarationRepo.save(declaration);
  }

  @Transactional(rollbackOn = {Exception.class})
  public void completeDeclaration(Declaration declaration) throws AxelorException {

    if (declaration.getStatusSelect() == null
        || declaration.getStatusSelect() != DeclarationRepository.STATUS_ORDER_CONFIRMED) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SaleExceptionMessage.SALE_ORDER_COMPLETE_WRONG_STATUS));
    }

    declaration.setStatusSelect(DeclarationRepository.STATUS_ORDER_COMPLETED);
    declaration.setOrderBeingEdited(false);

    declarationRepo.save(declaration);
  }

  @Override
  public void saveDeclarationPDFAsAttachment(Declaration declaration) throws AxelorException {

    if (declaration.getPrintingSettings() == null) {
      if (declaration.getCompany().getPrintingSettings() != null) {
        declaration.setPrintingSettings(declaration.getCompany().getPrintingSettings());
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            String.format(
                I18n.get(SaleExceptionMessage.SALE_ORDER_MISSING_PRINTING_SETTINGS),
                declaration.getDeclarationSeq()),
            declaration);
      }
    }

    ReportFactory.createReport(IReport.SALES_ORDER, this.getFileName(declaration) + "-${date}")
        .addParam("Locale", ReportSettings.getPrintingLocale(declaration.getClientPartner()))
        .addParam(
            "Timezone",
            declaration.getCompany() != null ? declaration.getCompany().getTimezone() : null)
        .addParam("DeclarationId", declaration.getId())
        .addParam("HeaderHeight", declaration.getPrintingSettings().getPdfHeaderHeight())
        .addParam("FooterHeight", declaration.getPrintingSettings().getPdfFooterHeight())
        .addParam(
            "AddressPositionSelect", declaration.getPrintingSettings().getAddressPositionSelect())
        .toAttach(declaration)
        .generate()
        .getFileLink();

    //		String relatedModel = generalService.getPersistentClass(declaration).getCanonicalName();
    // required ?

  }

  @Override
  public String getFileName(Declaration declaration) {
    String fileNamePrefix;
    if (declaration.getStatusSelect() == DeclarationRepository.STATUS_DRAFT_QUOTATION
        || declaration.getStatusSelect() == DeclarationRepository.STATUS_FINALIZED_QUOTATION) {
      fileNamePrefix = "Sale quotation";
    } else {
      fileNamePrefix = "Sale order";
    }

    return I18n.get(fileNamePrefix)
        + " "
        + declaration.getDeclarationSeq()
        + ((declaration.getVersionNumber() > 1) ? "-V" + declaration.getVersionNumber() : "");
  }

  /**
   * Throws exceptions to block the finalization of given sale order.
   *
   * @param declaration a sale order being finalized
   */
  protected void checkDeclarationBeforeFinalization(Declaration declaration) throws AxelorException {
    Beans.get(DeclarationService.class).checkUnauthorizedDiscounts(declaration);
  }
}
