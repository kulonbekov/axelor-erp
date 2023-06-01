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
package com.axelor.apps.sale.service.declaration.print;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.exception.SaleExceptionMessage;
import com.axelor.apps.sale.report.IReport;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.utils.ModelTool;
import com.axelor.utils.ThrowConsumer;
import com.axelor.utils.file.PdfTool;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DeclarationPrintServiceImpl implements DeclarationPrintService {

  protected DeclarationService declarationService;
  protected AppSaleService appSaleService;

  @Inject
  public DeclarationPrintServiceImpl(
      DeclarationService declarationService, AppSaleService appSaleService) {
    this.declarationService = declarationService;
    this.appSaleService = appSaleService;
  }

  @Override
  public String printDeclaration(Declaration declaration, boolean proforma, String format)
      throws AxelorException, IOException {
    String fileName = declarationService.getFileName(declaration) + "." + format;

    return PdfTool.getFileLinkFromPdfFile(print(declaration, proforma, format), fileName);
  }

  @Override
  public String printDeclarations(List<Long> ids) throws IOException {
    List<File> printedDeclarations = new ArrayList<>();
    ModelTool.apply(
        Declaration.class,
        ids,
        new ThrowConsumer<Declaration, Exception>() {
          @Override
          public void accept(Declaration declaration) throws Exception {
            printedDeclarations.add(print(declaration, false, ReportSettings.FORMAT_PDF));
          }
        });
    Integer status = Beans.get(DeclarationRepository.class).find(ids.get(0)).getStatusSelect();
    String fileName = getDeclarationFilesName(status);
    return PdfTool.mergePdfToFileLink(printedDeclarations, fileName);
  }

  public File print(Declaration declaration, boolean proforma, String format) throws AxelorException {
    ReportSettings reportSettings = prepareReportSettings(declaration, proforma, format);
    return reportSettings.generate().getFile();
  }

  @Override
  public ReportSettings prepareReportSettings(Declaration declaration, boolean proforma, String format)
      throws AxelorException {

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
    String locale = ReportSettings.getPrintingLocale(declaration.getClientPartner());

    String title = declarationService.getFileName(declaration);

    ReportSettings reportSetting =
        ReportFactory.createReport(IReport.SALES_ORDER, title + " - ${date}");

    return reportSetting
        .addParam("DeclarationId", declaration.getId())
        .addParam(
            "Timezone",
            declaration.getCompany() != null ? declaration.getCompany().getTimezone() : null)
        .addParam("Locale", locale)
        .addParam("ProformaInvoice", proforma)
        .addParam("HeaderHeight", declaration.getPrintingSettings().getPdfHeaderHeight())
        .addParam("FooterHeight", declaration.getPrintingSettings().getPdfFooterHeight())
        .addParam(
            "AddressPositionSelect", declaration.getPrintingSettings().getAddressPositionSelect())
        .addFormat(format);
  }

  /** Return the name for the printed sale orders. */
  protected String getDeclarationFilesName(Integer status) {
    String prefixFileName = I18n.get("Sale orders");
    if (status == DeclarationRepository.STATUS_DRAFT_QUOTATION
        || status == DeclarationRepository.STATUS_FINALIZED_QUOTATION) {
      prefixFileName = I18n.get("Sale quotations");
    }

    return prefixFileName
        + " - "
        + appSaleService
            .getTodayDate(
                Optional.ofNullable(AuthUtils.getUser()).map(User::getActiveCompany).orElse(null))
            .format(DateTimeFormatter.BASIC_ISO_DATE)
        + "."
        + ReportSettings.FORMAT_PDF;
  }
}
