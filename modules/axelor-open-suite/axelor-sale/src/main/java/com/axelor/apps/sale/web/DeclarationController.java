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
package com.axelor.apps.sale.web;

import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.ResponseMessageType;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PrintingSettings;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PricedOrderDomainService;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.apps.base.service.exception.HandleExceptionResponse;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.Pack;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.PackRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.exception.SaleExceptionMessage;
import com.axelor.apps.sale.service.DeclarationDomainService;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationComputeService;
import com.axelor.apps.sale.service.declaration.DeclarationCreateService;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.sale.service.declaration.DeclarationMarginService;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.apps.sale.service.declaration.DeclarationWorkflowService;
import com.axelor.apps.sale.service.declaration.print.DeclarationPrintService;
import com.axelor.common.ObjectUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.utils.StringTool;
import com.axelor.utils.db.Wizard;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DeclarationController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void compute(ActionRequest request, ActionResponse response) {

    Declaration declaration = request.getContext().asType(Declaration.class);

    try {
      declaration = Beans.get(DeclarationComputeService.class).computeDeclaration(declaration);
      response.setValues(declaration);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void computeMargin(ActionRequest request, ActionResponse response) {

    Declaration declaration = request.getContext().asType(Declaration.class);

    try {
      Beans.get(DeclarationMarginService.class).computeMarginDeclaration(declaration);

      response.setValue("accountedRevenue", declaration.getAccountedRevenue());
      response.setValue("totalCostPrice", declaration.getTotalCostPrice());
      response.setValue("totalGrossMargin", declaration.getTotalGrossMargin());
      response.setValue("marginRate", declaration.getMarginRate());
      response.setValue("markup", declaration.getMarkup());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Print the sale order as a PDF.
   *
   * @param request
   * @param response
   */
  public void showDeclaration(ActionRequest request, ActionResponse response) {
    this.exportDeclaration(request, response, false, ReportSettings.FORMAT_PDF);
  }

  /**
   * Print a proforma invoice as a PDF.
   *
   * @param request
   * @param response
   */
  public void printProformaInvoice(ActionRequest request, ActionResponse response) {
    this.exportDeclaration(request, response, true, ReportSettings.FORMAT_PDF);
  }

  public void exportDeclarationExcel(ActionRequest request, ActionResponse response) {
    this.exportDeclaration(request, response, false, ReportSettings.FORMAT_XLSX);
  }

  public void exportDeclarationWord(ActionRequest request, ActionResponse response) {
    this.exportDeclaration(request, response, false, ReportSettings.FORMAT_DOC);
  }

  @SuppressWarnings("unchecked")
  public void exportDeclaration(
      ActionRequest request, ActionResponse response, boolean proforma, String format) {

    Context context = request.getContext();
    String fileLink;
    String title;
    DeclarationPrintService declarationPrintService = Beans.get(DeclarationPrintService.class);

    try {
      if (!ObjectUtils.isEmpty(request.getContext().get("_ids"))) {
        List<Long> ids =
            Lists.transform(
                (List) request.getContext().get("_ids"),
                new Function<Object, Long>() {
                  @Nullable
                  @Override
                  public Long apply(@Nullable Object input) {
                    return Long.parseLong(input.toString());
                  }
                });
        fileLink = declarationPrintService.printDeclarations(ids);
        title = I18n.get("Sale orders");

      } else if (context.get("id") != null) {

        Declaration declaration =
            Beans.get(DeclarationRepository.class).find(Long.parseLong(context.get("id").toString()));
        title = Beans.get(DeclarationService.class).getFileName(declaration);
        fileLink = declarationPrintService.printDeclaration(declaration, proforma, format);
        response.setCanClose(true);

        logger.debug("Printing " + title);
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(SaleExceptionMessage.SALE_ORDER_PRINT));
      }
      response.setView(ActionView.define(title).add("html", fileLink).map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void cancelDeclaration(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);

      Beans.get(DeclarationWorkflowService.class)
          .cancelDeclaration(
              Beans.get(DeclarationRepository.class).find(declaration.getId()),
              declaration.getCancelReason(),
              declaration.getCancelReasonStr());

      response.setInfo(I18n.get("The sale order was canceled"));
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void finalizeQuotation(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());

    try {
      Beans.get(DeclarationWorkflowService.class).finalizeQuotation(declaration);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }

    response.setReload(true);
  }

  public void completeDeclaration(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());

    try {
      Beans.get(DeclarationWorkflowService.class).completeDeclaration(declaration);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }

    response.setReload(true);
  }

  public void confirmDeclaration(ActionRequest request, ActionResponse response) {

    try {
      Declaration declaration = request.getContext().asType(Declaration.class);

      Beans.get(DeclarationWorkflowService.class)
          .confirmDeclaration(Beans.get(DeclarationRepository.class).find(declaration.getId()));

      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  @SuppressWarnings("unchecked")
  public void generateViewDeclaration(ActionRequest request, ActionResponse response) {
    LinkedHashMap<String, Object> declarationTemplateContext =
        (LinkedHashMap<String, Object>) request.getContext().get("_declarationTemplate");
    Integer declarationId = (Integer) declarationTemplateContext.get("id");
    Declaration context = Beans.get(DeclarationRepository.class).find(Long.valueOf(declarationId));

    response.setView(
        ActionView.define(I18n.get("Sale order"))
            .model(Declaration.class.getName())
            .add("form", "sale-order-form-wizard")
            .context("_idCopy", context.getId().toString())
            .context("_wizardCurrency", request.getContext().get("currency"))
            .context("_wizardPriceList", request.getContext().get("priceList"))
            .map());

    response.setCanClose(true);
  }

  public void generateViewTemplate(ActionRequest request, ActionResponse response) {
    Declaration context = request.getContext().asType(Declaration.class);
    response.setView(
        ActionView.define(I18n.get("Template"))
            .model(Declaration.class.getName())
            .add("form", "sale-order-template-form-wizard")
            .context("_idCopy", context.getId().toString())
            .map());
  }

  public void generateDeclarationWizard(ActionRequest request, ActionResponse response) {
    Declaration declarationTemplate = request.getContext().asType(Declaration.class);
    Partner clientPartner = declarationTemplate.getClientPartner();

    response.setView(
        ActionView.define(I18n.get("Create the quotation"))
            .model(Wizard.class.getName())
            .add("form", "sale-order-template-wizard-form")
            .param("popup", "reload")
            .param("show-toolbar", "false")
            .param("show-confirm", "false")
            .param("width", "large")
            .param("popup-save", "false")
            .context("_declarationTemplate", declarationTemplate)
            .context("_clientPartnerCurrency", clientPartner.getCurrency())
            .map());
  }

  @SuppressWarnings("unchecked")
  public void createDeclaration(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Declaration origin =
        Beans.get(DeclarationRepository.class)
            .find(Long.parseLong(request.getContext().get("_idCopy").toString()));

    if (origin != null) {
      LinkedHashMap<String, Object> wizardCurrencyContext =
          (LinkedHashMap<String, Object>) request.getContext().get("_wizardCurrency");
      Integer wizardCurrencyId = (Integer) wizardCurrencyContext.get("id");
      Currency wizardCurrency =
          Beans.get(CurrencyRepository.class).find(Long.valueOf(wizardCurrencyId));

      PriceList wizardPriceList = null;
      if (request.getContext().get("_wizardPriceList") != null) {
        LinkedHashMap<String, Object> wizardPriceListContext =
            (LinkedHashMap<String, Object>) request.getContext().get("_wizardPriceList");
        Integer wizardPriceListId = (Integer) wizardPriceListContext.get("id");
        wizardPriceList =
            Beans.get(PriceListRepository.class).find(Long.valueOf(wizardPriceListId));
      }

      Declaration copy =
          Beans.get(DeclarationCreateService.class)
              .createDeclaration(origin, wizardCurrency, wizardPriceList);
      response.setValues(Mapper.toMap(copy));
    }
  }

  public void createTemplate(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    if (context.get("_idCopy") != null) {
      String idCopy = context.get("_idCopy").toString();
      Declaration origin = Beans.get(DeclarationRepository.class).find(Long.parseLong(idCopy));
      Declaration copy = Beans.get(DeclarationCreateService.class).createTemplate(origin);
      response.setValues(Mapper.toMap(copy));
    }
  }

  public void computeEndOfValidityDate(ActionRequest request, ActionResponse response) {

    Declaration declaration = request.getContext().asType(Declaration.class);

    try {
      declaration = Beans.get(DeclarationService.class).computeEndOfValidityDate(declaration);
      response.setValue("endOfValidityDate", declaration.getEndOfValidityDate());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Set the address string with their values.
   *
   * @param request
   * @param response
   */
  public void computeAddressStr(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    Beans.get(DeclarationService.class).computeAddressStr(declaration);

    response.setValues(declaration);
  }

  /**
   * Called on partner, company or payment change. Fill the bank details with a default value.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void fillCompanyBankDetails(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Declaration declaration = request.getContext().asType(Declaration.class);
    PaymentMode paymentMode = (PaymentMode) request.getContext().get("paymentMode");
    Company company = declaration.getCompany();
    Partner partner = declaration.getClientPartner();
    if (company == null) {
      return;
    }
    if (partner != null) {
      partner = Beans.get(PartnerRepository.class).find(partner.getId());
    }
    BankDetails defaultBankDetails =
        Beans.get(BankDetailsService.class)
            .getDefaultCompanyBankDetails(company, paymentMode, partner, null);
    response.setValue("companyBankDetails", defaultBankDetails);
  }

  public void enableEditOrder(ActionRequest request, ActionResponse response) {
    Declaration declaration =
        Beans.get(DeclarationRepository.class)
            .find(request.getContext().asType(Declaration.class).getId());

    try {
      boolean checkAvailabiltyRequest =
          Beans.get(DeclarationService.class).enableEditOrder(declaration);
      response.setReload(true);
      if (checkAvailabiltyRequest) {
        response.setNotify(I18n.get(SaleExceptionMessage.SALE_ORDER_EDIT_ORDER_NOTIFY));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order form view, on clicking validate change button. Call {@link
   * DeclarationService#validateChanges(Declaration)}.
   *
   * @param request
   * @param response
   */
  public void validateChanges(ActionRequest request, ActionResponse response) {
    try {
      Declaration declarationView = request.getContext().asType(Declaration.class);
      Declaration declaration = Beans.get(DeclarationRepository.class).find(declarationView.getId());
      Beans.get(DeclarationService.class).validateChanges(declaration);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called on printing settings select. Set the domain for {@link Declaration#printingSettings}
   *
   * @param request
   * @param response
   */
  public void filterPrintingSettings(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      List<PrintingSettings> printingSettingsList =
          Beans.get(TradingNameService.class)
              .getPrintingSettingsList(declaration.getTradingName(), declaration.getCompany());
      String domain =
          String.format(
              "self.id IN (%s)",
              !printingSettingsList.isEmpty()
                  ? StringTool.getIdListString(printingSettingsList)
                  : "0");
      response.setAttr("printingSettings", "domain", domain);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called on trading name change. Set the default value for {@link Declaration#printingSettings}
   *
   * @param request
   * @param response
   */
  public void fillDefaultPrintingSettings(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      response.setValue(
          "printingSettings",
          Beans.get(TradingNameService.class)
              .getDefaultPrintingSettings(declaration.getTradingName(), declaration.getCompany()));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order form view on partner change. Get the default price list for the sale
   * order. Call {@link PartnerPriceListService#getDefaultPriceList(Partner, int)}.
   *
   * @param request
   * @param response
   */
  @SuppressWarnings("unchecked")
  public void fillPriceList(ActionRequest request, ActionResponse response) {
    Declaration declaration;
    if (request.getContext().get("_declarationTemplate") != null) {
      LinkedHashMap<String, Object> declarationTemplateContext =
          (LinkedHashMap<String, Object>) request.getContext().get("_declarationTemplate");
      Integer declarationId = (Integer) declarationTemplateContext.get("id");
      declaration = Beans.get(DeclarationRepository.class).find(Long.valueOf(declarationId));
    } else {
      declaration = request.getContext().asType(Declaration.class);
    }
    response.setValue(
        "priceList",
        declaration.getClientPartner() != null
            ? Beans.get(PartnerPriceListService.class)
                .getDefaultPriceList(declaration.getClientPartner(), PriceListRepository.TYPE_SALE)
            : null);
  }

  /**
   * Called from sale order view on price list select. Call {@link
   * PartnerPriceListService#getPriceListDomain(Partner, int)}.
   *
   * @param request
   * @param response
   */
  @SuppressWarnings("unchecked")
  public void changePriceListDomain(ActionRequest request, ActionResponse response) {
    Declaration declaration;
    if (request.getContext().get("_declarationTemplate") != null) {
      LinkedHashMap<String, Object> declarationTemplateContext =
          (LinkedHashMap<String, Object>) request.getContext().get("_declarationTemplate");
      Integer declarationId = (Integer) declarationTemplateContext.get("id");
      declaration = Beans.get(DeclarationRepository.class).find(Long.valueOf(declarationId));
    } else {
      declaration = request.getContext().asType(Declaration.class);
    }
    String domain =
        Beans.get(PartnerPriceListService.class)
            .getPriceListDomain(declaration.getClientPartner(), PriceListRepository.TYPE_SALE);
    response.setAttr("priceList", "domain", domain);
  }

  public void updateDeclarationLineList(ActionRequest request, ActionResponse response)
      throws AxelorException {

    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      Beans.get(DeclarationCreateService.class).updateDeclarationLineList(declaration);
      response.setValue("declarationLineList", declaration.getDeclarationLineList());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void addPack(ActionRequest request, ActionResponse response) {
    try {

      Context context = request.getContext();

      String declarationId = context.get("_id").toString();
      Declaration declaration = Beans.get(DeclarationRepository.class).find(Long.parseLong(declarationId));

      @SuppressWarnings("unchecked")
      LinkedHashMap<String, Object> packMap =
          (LinkedHashMap<String, Object>) request.getContext().get("pack");
      String packId = packMap.get("id").toString();
      Pack pack = Beans.get(PackRepository.class).find(Long.parseLong(packId));

      String qty = context.get("qty").toString();
      BigDecimal packQty = new BigDecimal(qty);

      declaration = Beans.get(DeclarationService.class).addPack(declaration, pack, packQty);

      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void getDeclarationPartnerDomain(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
      String domain =
          Beans.get(DeclarationDomainService.class).getPartnerBaseDomain(declaration.getCompany());

      if (!(declarationLineList == null || declarationLineList.isEmpty())) {
        domain =
            Beans.get(PricedOrderDomainService.class)
                .getPartnerDomain(declaration, domain, PriceListRepository.TYPE_SALE);
      }

      response.setAttr("clientPartner", "domain", domain);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void handleComplementaryProducts(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);

    try {
      declaration.setDeclarationLineList(
          Beans.get(DeclarationService.class).handleComplementaryProducts(declaration));
      response.setValues(declaration);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void updateProductQtyWithPackHeaderQty(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    if (Boolean.FALSE.equals(Beans.get(AppSaleService.class).getAppSale().getEnablePackManagement())
        || !Beans.get(DeclarationLineService.class)
            .isStartOfPackTypeLineQtyChanged(declaration.getDeclarationLineList())) {
      return;
    }
    try {
      Beans.get(DeclarationService.class).updateProductQtyWithPackHeaderQty(declaration);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
    response.setReload(true);
  }

  @HandleExceptionResponse
  public void separateInNewQuotation(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Set<Entry<String, Object>> contextEntry = request.getContext().entrySet();
    Optional<Entry<String, Object>> declarationLineEntries =
        contextEntry.stream()
            .filter(entry -> entry.getKey().equals("declarationLineList"))
            .findFirst();
    if (!declarationLineEntries.isPresent()) {
      return;
    }

    Entry<String, Object> entry = declarationLineEntries.get();
    @SuppressWarnings("unchecked")
    ArrayList<LinkedHashMap<String, Object>> declarationLines =
        (ArrayList<LinkedHashMap<String, Object>>) entry.getValue();

    Declaration declaration = request.getContext().asType(Declaration.class);
    Declaration copiedSO =
        Beans.get(DeclarationService.class).separateInNewQuotation(declaration, declarationLines);
    response.setView(
        ActionView.define(I18n.get("Sale order"))
            .model(Declaration.class.getName())
            .add("form", "sale-order-form")
            .add("grid", "sale-order-grid")
            .param("forceEdit", "true")
            .context("_showRecord", copiedSO.getId())
            .map());
  }

  /**
   * Empty the fiscal position field if its value is no longer compatible with the new taxNumber
   * after a change
   *
   * @param request
   * @param response
   */
  public void emptyFiscalPositionIfNotCompatible(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      FiscalPosition soFiscalPosition = declaration.getFiscalPosition();
      if (soFiscalPosition == null) {
        return;
      }
      if (declaration.getTaxNumber() == null) {
        if (declaration.getClientPartner() != null
            && declaration.getFiscalPosition() == declaration.getClientPartner().getFiscalPosition()) {
          return;
        }
      } else {
        for (FiscalPosition fiscalPosition : declaration.getTaxNumber().getFiscalPositionSet()) {
          if (fiscalPosition.getId().equals(soFiscalPosition.getId())) {
            return;
          }
        }
      }
      response.setValue("fiscalPosition", null);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order form view upon changing the fiscalPosition (directly or via changing the
   * taxNumber) Updates taxLine, taxEquiv and prices by calling {@link
   * DeclarationLineService#fillPrice(DeclarationLine, Declaration)}.
   *
   * @param request
   * @param response
   */
  public void updateLinesAfterFiscalPositionChange(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      if (declaration.getDeclarationLineList() != null) {
        Beans.get(DeclarationLineService.class).updateLinesAfterFiscalPositionChange(declaration);
      }
      response.setValue("declarationLineList", declaration.getDeclarationLineList());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
