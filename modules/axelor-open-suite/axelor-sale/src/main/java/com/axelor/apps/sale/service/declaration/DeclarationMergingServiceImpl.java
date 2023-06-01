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
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.exception.SaleExceptionMessage;
import com.axelor.i18n.I18n;
import com.axelor.rpc.Context;
import com.axelor.team.db.Team;
import com.axelor.utils.MapTools;
import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class DeclarationMergingServiceImpl implements DeclarationMergingService {

  protected static class CommonFieldsImpl implements CommonFields {

    private Company commonCompany = null;
    private Currency commonCurrency = null;
    private Partner commonClientPartner = null;
    private TaxNumber commonTaxNumber = null;
    private FiscalPosition commonFiscalPosition = null;
    private Team commonTeam = null;
    private Partner commonContactPartner = null;
    private PriceList commonPriceList = null;

    @Override
    public Company getCommonCompany() {
      return commonCompany;
    }

    @Override
    public void setCommonCompany(Company commonCompany) {
      this.commonCompany = commonCompany;
    }

    @Override
    public Currency getCommonCurrency() {
      return commonCurrency;
    }

    @Override
    public void setCommonCurrency(Currency commonCurrency) {
      this.commonCurrency = commonCurrency;
    }

    @Override
    public Partner getCommonClientPartner() {
      return this.commonClientPartner;
    }

    @Override
    public void setCommonClientPartner(Partner commonClientPartner) {
      this.commonClientPartner = commonClientPartner;
    }

    @Override
    public TaxNumber getCommonTaxNumber() {
      return commonTaxNumber;
    }

    @Override
    public void setCommonTaxNumber(TaxNumber commonTaxNumber) {
      this.commonTaxNumber = commonTaxNumber;
    }

    @Override
    public FiscalPosition getCommonFiscalPosition() {
      return commonFiscalPosition;
    }

    @Override
    public void setCommonFiscalPosition(FiscalPosition commonFiscalPosition) {
      this.commonFiscalPosition = commonFiscalPosition;
    }

    @Override
    public Team getCommonTeam() {
      return commonTeam;
    }

    @Override
    public void setCommonTeam(Team commonTeam) {
      this.commonTeam = commonTeam;
    }

    @Override
    public Partner getCommonContactPartner() {
      return commonContactPartner;
    }

    @Override
    public void setCommonContactPartner(Partner commonContactPartner) {
      this.commonContactPartner = commonContactPartner;
    }

    @Override
    public PriceList getCommonPriceList() {
      return commonPriceList;
    }

    @Override
    public void setCommonPriceList(PriceList commonPriceList) {
      this.commonPriceList = commonPriceList;
    }
  }

  protected static class ChecksImpl implements Checks {

    private boolean existCurrencyDiff = false;
    private boolean existCompanyDiff = false;
    private boolean existClientPartnerDiff = false;
    private boolean existTaxNumberDiff = false;
    private boolean existFiscalPositionDiff = false;
    private boolean existTeamDiff = false;
    private boolean existContactPartnerDiff = false;
    private boolean existPriceListDiff = false;

    @Override
    public boolean isExistCurrencyDiff() {
      return existCurrencyDiff;
    }

    @Override
    public void setExistCurrencyDiff(boolean existCurrencyDiff) {
      this.existCurrencyDiff = existCurrencyDiff;
    }

    @Override
    public boolean isExistCompanyDiff() {
      return existCompanyDiff;
    }

    @Override
    public void setExistCompanyDiff(boolean existCompanyDiff) {
      this.existCompanyDiff = existCompanyDiff;
    }

    @Override
    public boolean isExistClientPartnerDiff() {
      return existClientPartnerDiff;
    }

    @Override
    public void setExistClientPartnerDiff(boolean existClientPartnerDiff) {
      this.existClientPartnerDiff = existClientPartnerDiff;
    }

    @Override
    public boolean isExistTaxNumberDiff() {
      return existTaxNumberDiff;
    }

    @Override
    public void setExistTaxNumberDiff(boolean existTaxNumberDiff) {
      this.existTaxNumberDiff = existTaxNumberDiff;
    }

    @Override
    public boolean isExistFiscalPositionDiff() {
      return existFiscalPositionDiff;
    }

    @Override
    public void setExistFiscalPositionDiff(boolean existFiscalPositionDiff) {
      this.existFiscalPositionDiff = existFiscalPositionDiff;
    }

    @Override
    public boolean isExistTeamDiff() {
      return existTeamDiff;
    }

    @Override
    public void setExistTeamDiff(boolean existTeamDiff) {
      this.existTeamDiff = existTeamDiff;
    }

    @Override
    public boolean isExistContactPartnerDiff() {
      return existContactPartnerDiff;
    }

    @Override
    public void setExistContactPartnerDiff(boolean existContactPartnerDiff) {
      this.existContactPartnerDiff = existContactPartnerDiff;
    }

    @Override
    public boolean isExistPriceListDiff() {
      return existPriceListDiff;
    }

    @Override
    public void setExistPriceListDiff(boolean existPriceListDiff) {
      this.existPriceListDiff = existPriceListDiff;
    }
  }

  protected static class DeclarationMergingResultImpl implements DeclarationMergingResult {

    private Declaration declaration;
    private boolean isConfirmationNeeded;
    private final CommonFieldsImpl commonFields;
    private final ChecksImpl checks;

    public DeclarationMergingResultImpl() {
      this.declaration = null;
      this.isConfirmationNeeded = false;
      this.commonFields = new CommonFieldsImpl();
      this.checks = new ChecksImpl();
    }

    public Declaration getDeclaration() {
      return declaration;
    }

    public void setDeclaration(Declaration declaration) {
      this.declaration = declaration;
    }

    @Override
    public void needConfirmation() {
      this.isConfirmationNeeded = true;
    }

    @Override
    public boolean isConfirmationNeeded() {
      return isConfirmationNeeded;
    }
  }

  protected DeclarationCreateService declarationCreateService;

  @Inject
  public DeclarationMergingServiceImpl(DeclarationCreateService declarationCreateService) {
    this.declarationCreateService = declarationCreateService;
  }

  @Override
  public DeclarationMergingResultImpl create() {
    return new DeclarationMergingResultImpl();
  }

  @Override
  public CommonFieldsImpl getCommonFields(DeclarationMergingResult result) {
    return ((DeclarationMergingResultImpl) result).commonFields;
  }

  @Override
  public ChecksImpl getChecks(DeclarationMergingResult result) {
    return ((DeclarationMergingResultImpl) result).checks;
  }

  @Override
  public DeclarationMergingResult mergeDeclarations(List<Declaration> declarationsToMerge)
      throws AxelorException {
    Objects.requireNonNull(declarationsToMerge);
    DeclarationMergingResult result = controlDeclarationsToMerge(declarationsToMerge);

    if (isConfirmationNeeded(result)) {
      result.needConfirmation();
      return result;
    }
    result.setDeclaration(mergeDeclarations(declarationsToMerge, result));
    return result;
  }

  @Override
  public DeclarationMergingResult mergeDeclarationsWithContext(
      List<Declaration> declarationsToMerge, Context context) throws AxelorException {
    Objects.requireNonNull(declarationsToMerge);
    Objects.requireNonNull(context);

    DeclarationMergingResult result = controlDeclarationsToMerge(declarationsToMerge);
    updateResultWithContext(result, context);
    result.setDeclaration(mergeDeclarations(declarationsToMerge, result));
    return result;
  }

  protected void updateResultWithContext(DeclarationMergingResult result, Context context) {
    if (context.get("priceList") != null) {
      getCommonFields(result)
          .setCommonPriceList(MapTools.findObject(PriceList.class, context.get("priceList")));
    }
    if (context.get("contactPartner") != null) {
      getCommonFields(result)
          .setCommonContactPartner(
              MapTools.findObject(Partner.class, context.get("contactPartner")));
    }
    if (context.get("team") != null) {
      getCommonFields(result).setCommonTeam(MapTools.findObject(Team.class, context.get("team")));
    }
  }

  protected DeclarationMergingResult controlDeclarationsToMerge(List<Declaration> declarationsToMerge)
      throws AxelorException {
    DeclarationMergingResult result = create();

    if (declarationsToMerge.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SaleExceptionMessage.SALE_ORDER_MERGE_LIST_EMPTY));
    }

    Declaration firstDeclaration = declarationsToMerge.get(0);
    fillCommonFields(firstDeclaration, result);
    declarationsToMerge.stream()
        .skip(1)
        .forEach(
            declaration -> {
              updateDiffsCommonFields(declaration, result);
            });

    StringJoiner fieldErrors = new StringJoiner("<BR/>");
    checkErrors(fieldErrors, result);
    if (fieldErrors.length() > 0) {
      throw new AxelorException(TraceBackRepository.CATEGORY_INCONSISTENCY, fieldErrors.toString());
    }
    return result;
  }

  protected boolean isConfirmationNeeded(DeclarationMergingResult result) {

    return getChecks(result).isExistContactPartnerDiff()
        || getChecks(result).isExistPriceListDiff()
        || getChecks(result).isExistTeamDiff();
  }

  protected Declaration mergeDeclarations(
      List<Declaration> declarationsToMerge, DeclarationMergingResult result) throws AxelorException {
    return declarationCreateService.mergeDeclarations(
        declarationsToMerge,
        getCommonFields(result).getCommonCurrency(),
        getCommonFields(result).getCommonClientPartner(),
        getCommonFields(result).getCommonCompany(),
        getCommonFields(result).getCommonContactPartner(),
        getCommonFields(result).getCommonPriceList(),
        getCommonFields(result).getCommonTeam(),
        getCommonFields(result).getCommonTaxNumber(),
        getCommonFields(result).getCommonFiscalPosition());
  }

  protected void checkErrors(StringJoiner fieldErrors, DeclarationMergingResult result) {
    if (getChecks(result).isExistCurrencyDiff()
        || getCommonFields(result).getCommonCurrency() == null) {
      fieldErrors.add(I18n.get(SaleExceptionMessage.SALE_ORDER_MERGE_ERROR_CURRENCY));
    }
    if (getChecks(result).isExistClientPartnerDiff()
        || getCommonFields(result).getCommonClientPartner() == null) {
      fieldErrors.add(I18n.get(SaleExceptionMessage.SALE_ORDER_MERGE_ERROR_CLIENT_PARTNER));
    }
    if (getChecks(result).isExistCompanyDiff()
        || getCommonFields(result).getCommonCompany() == null) {
      fieldErrors.add(I18n.get(SaleExceptionMessage.SALE_ORDER_MERGE_ERROR_COMPANY));
    }
    // TaxNumber can be null
    if (getChecks(result).isExistTaxNumberDiff()) {
      fieldErrors.add(I18n.get(SaleExceptionMessage.SALE_ORDER_MERGE_ERROR_TAX_NUMBER));
    }
    // FiscalPosition can be null
    if (getChecks(result).isExistFiscalPositionDiff()) {
      fieldErrors.add(I18n.get(SaleExceptionMessage.SALE_ORDER_MERGE_ERROR_FISCAL_POSITION));
    }
  }

  protected void updateDiffsCommonFields(Declaration declaration, DeclarationMergingResult result) {
    CommonFields commonFields = getCommonFields(result);
    Checks checks = getChecks(result);
    if ((commonFields.getCommonCurrency() == null ^ declaration.getCurrency() == null)
        || (commonFields.getCommonCurrency() != declaration.getCurrency()
            && !commonFields.getCommonCurrency().equals(declaration.getCurrency()))) {
      commonFields.setCommonCurrency(null);
      checks.setExistCurrencyDiff(true);
    }
    if ((commonFields.getCommonClientPartner() == null ^ declaration.getClientPartner() == null)
        || (commonFields.getCommonClientPartner() != declaration.getClientPartner()
            && !commonFields.getCommonClientPartner().equals(declaration.getClientPartner()))) {
      commonFields.setCommonClientPartner(null);
      checks.setExistClientPartnerDiff(true);
    }
    if ((commonFields.getCommonCompany() == null ^ declaration.getCompany() == null)
        || (commonFields.getCommonCompany() != declaration.getCompany()
            && !commonFields.getCommonCompany().equals(declaration.getCompany()))) {
      commonFields.setCommonCompany(null);
      checks.setExistCompanyDiff(true);
    }
    if ((commonFields.getCommonContactPartner() == null ^ declaration.getContactPartner() == null)
        || (commonFields.getCommonContactPartner() != declaration.getContactPartner()
            && !commonFields.getCommonContactPartner().equals(declaration.getContactPartner()))) {
      commonFields.setCommonContactPartner(null);
      checks.setExistContactPartnerDiff(true);
    }
    if ((commonFields.getCommonTeam() == null ^ declaration.getTeam() == null)
        || (commonFields.getCommonTeam() != declaration.getTeam()
            && !commonFields.getCommonTeam().equals(declaration.getTeam()))) {
      commonFields.setCommonTeam(null);
      checks.setExistTeamDiff(true);
    }
    if ((commonFields.getCommonPriceList() == null ^ declaration.getPriceList() == null)
        || (commonFields.getCommonPriceList() != declaration.getPriceList()
            && !commonFields.getCommonPriceList().equals(declaration.getPriceList()))) {
      commonFields.setCommonPriceList(null);
      checks.setExistPriceListDiff(true);
    }
    if ((commonFields.getCommonTaxNumber() == null ^ declaration.getTaxNumber() == null)
        || (commonFields.getCommonTaxNumber() != declaration.getTaxNumber()
            && !commonFields.getCommonTaxNumber().equals(declaration.getTaxNumber()))) {
      commonFields.setCommonTaxNumber(null);
      checks.setExistTaxNumberDiff(true);
    }
    if ((commonFields.getCommonFiscalPosition() == null ^ declaration.getFiscalPosition() == null)
        || (commonFields.getCommonFiscalPosition() != declaration.getFiscalPosition()
            && !commonFields.getCommonFiscalPosition().equals(declaration.getFiscalPosition()))) {
      commonFields.setCommonFiscalPosition(null);
      checks.setExistFiscalPositionDiff(true);
    }
  }

  protected void fillCommonFields(Declaration firstDeclaration, DeclarationMergingResult result) {
    CommonFields commonFields = getCommonFields(result);
    commonFields.setCommonCompany(firstDeclaration.getCompany());
    commonFields.setCommonCurrency(firstDeclaration.getCurrency());
    commonFields.setCommonContactPartner(firstDeclaration.getContactPartner());
    commonFields.setCommonFiscalPosition(firstDeclaration.getFiscalPosition());
    commonFields.setCommonPriceList(firstDeclaration.getPriceList());
    commonFields.setCommonTaxNumber(firstDeclaration.getTaxNumber());
    commonFields.setCommonTeam(firstDeclaration.getTeam());
    commonFields.setCommonClientPartner(firstDeclaration.getClientPartner());
  }
}
