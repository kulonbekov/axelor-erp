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
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationCreateService;
import com.axelor.apps.sale.service.declaration.DeclarationMergingServiceImpl;
import com.axelor.apps.stock.db.Incoterm;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.Context;
import com.axelor.utils.MapTools;
import com.google.inject.Inject;
import java.util.List;
import java.util.StringJoiner;

public class DeclarationMergingServiceSupplyChainImpl extends DeclarationMergingServiceImpl {

  protected static class CommonFieldsSupplyChainImpl extends CommonFieldsImpl {
    private StockLocation commonStockLocation = null;
    private Incoterm commonIncoterm = null;
    private Partner commonInvoicedPartner = null;
    private Partner commonDeliveredPartner = null;

    public StockLocation getCommonStockLocation() {
      return commonStockLocation;
    }

    public void setCommonStockLocation(StockLocation commonStockLocation) {
      this.commonStockLocation = commonStockLocation;
    }

    public Incoterm getCommonIncoterm() {
      return commonIncoterm;
    }

    public void setCommonIncoterm(Incoterm commonIncoterm) {
      this.commonIncoterm = commonIncoterm;
    }

    public Partner getCommonInvoicedPartner() {
      return commonInvoicedPartner;
    }

    public void setCommonInvoicedPartner(Partner commonInvoicedPartner) {
      this.commonInvoicedPartner = commonInvoicedPartner;
    }

    public Partner getCommonDeliveredPartner() {
      return commonDeliveredPartner;
    }

    public void setCommonDeliveredPartner(Partner commonDeliveredPartner) {
      this.commonDeliveredPartner = commonDeliveredPartner;
    }
  }

  protected static class ChecksSupplyChainImpl extends ChecksImpl {
    private boolean existStockLocationDiff = false;
    private boolean existIncotermDiff = false;
    private boolean existInvoicedPartnerDiff = false;
    private boolean existDeliveredPartnerDiff = false;

    public boolean isExistStockLocationDiff() {
      return existStockLocationDiff;
    }

    public void setExistStockLocationDiff(boolean existStockLocationDiff) {
      this.existStockLocationDiff = existStockLocationDiff;
    }

    public boolean isExistIncotermDiff() {
      return existIncotermDiff;
    }

    public void setExistIncotermDiff(boolean existIncotermDiff) {
      this.existIncotermDiff = existIncotermDiff;
    }

    public boolean isExistInvoicedPartnerDiff() {
      return existInvoicedPartnerDiff;
    }

    public void setExistInvoicedPartnerDiff(boolean existInvoicedPartnerDiff) {
      this.existInvoicedPartnerDiff = existInvoicedPartnerDiff;
    }

    public boolean isExistDeliveredPartnerDiff() {
      return existDeliveredPartnerDiff;
    }

    public void setExistDeliveredPartnerDiff(boolean existDeliveredPartnerDiff) {
      this.existDeliveredPartnerDiff = existDeliveredPartnerDiff;
    }
  }

  protected static class DeclarationMergingResultSupplyChainImpl extends DeclarationMergingResultImpl {
    private final CommonFieldsSupplyChainImpl commonFields;
    private final ChecksSupplyChainImpl checks;

    public DeclarationMergingResultSupplyChainImpl() {
      super();
      this.commonFields = new CommonFieldsSupplyChainImpl();
      this.checks = new ChecksSupplyChainImpl();
    }
  }

  protected AppSaleService appSaleService;
  protected AppSupplychainService appSupplyChainService;

  @Inject
  public DeclarationMergingServiceSupplyChainImpl(
      DeclarationCreateService saleOrdreCreateService,
      AppSaleService appSaleService,
      AppSupplychainService appSupplyChainService) {
    super(saleOrdreCreateService);
    this.appSaleService = appSaleService;
    this.appSupplyChainService = appSupplyChainService;
  }

  @Override
  public DeclarationMergingResultSupplyChainImpl create() {
    return new DeclarationMergingResultSupplyChainImpl();
  }

  @Override
  public CommonFieldsSupplyChainImpl getCommonFields(DeclarationMergingResult result) {
    return ((DeclarationMergingResultSupplyChainImpl) result).commonFields;
  }

  @Override
  public ChecksSupplyChainImpl getChecks(DeclarationMergingResult result) {
    return ((DeclarationMergingResultSupplyChainImpl) result).checks;
  }

  @Override
  protected boolean isConfirmationNeeded(DeclarationMergingResult result) {
    if (!appSaleService.isApp("supplychain")) {
      return super.isConfirmationNeeded(result);
    }
    return super.isConfirmationNeeded(result) || getChecks(result).existStockLocationDiff;
  }

  @Override
  protected void fillCommonFields(Declaration firstDeclaration, DeclarationMergingResult result) {
    super.fillCommonFields(firstDeclaration, result);
    if (appSaleService.isApp("supplychain")) {
      getCommonFields(result).setCommonStockLocation(firstDeclaration.getStockLocation());
      getCommonFields(result).setCommonIncoterm(firstDeclaration.getIncoterm());
      if (appSupplyChainService.getAppSupplychain().getActivatePartnerRelations()) {
        getCommonFields(result).setCommonInvoicedPartner(firstDeclaration.getInvoicedPartner());
        getCommonFields(result).setCommonDeliveredPartner(firstDeclaration.getDeliveredPartner());
      }
    }
  }

  @Override
  protected void updateDiffsCommonFields(Declaration declaration, DeclarationMergingResult result) {
    super.updateDiffsCommonFields(declaration, result);
    if (appSaleService.isApp("supplychain")) {
      CommonFieldsSupplyChainImpl commonFields = getCommonFields(result);
      ChecksSupplyChainImpl checks = getChecks(result);
      if ((commonFields.getCommonStockLocation() == null ^ declaration.getStockLocation() == null)
          || (commonFields.getCommonStockLocation() != declaration.getStockLocation()
              && !commonFields.getCommonStockLocation().equals(declaration.getStockLocation()))) {
        commonFields.setCommonStockLocation(null);
        checks.setExistStockLocationDiff(true);
      }
      if ((commonFields.getCommonIncoterm() == null ^ declaration.getIncoterm() == null)
          || (commonFields.getCommonIncoterm() != declaration.getIncoterm()
              && !commonFields.getCommonIncoterm().equals(declaration.getIncoterm()))) {
        commonFields.setCommonIncoterm(null);
        checks.setExistIncotermDiff(true);
      }
      if (appSupplyChainService.getAppSupplychain().getActivatePartnerRelations()) {
        if ((commonFields.getCommonInvoicedPartner() == null
                ^ declaration.getInvoicedPartner() == null)
            || (commonFields.getCommonInvoicedPartner() != declaration.getInvoicedPartner()
                && !commonFields
                    .getCommonInvoicedPartner()
                    .equals(declaration.getInvoicedPartner()))) {
          commonFields.setCommonInvoicedPartner(null);
          checks.setExistInvoicedPartnerDiff(true);
        }
        if ((commonFields.getCommonDeliveredPartner() == null
                ^ declaration.getDeliveredPartner() == null)
            || (commonFields.getCommonDeliveredPartner() != declaration.getDeliveredPartner()
                && !commonFields
                    .getCommonDeliveredPartner()
                    .equals(declaration.getDeliveredPartner()))) {
          commonFields.setCommonDeliveredPartner(null);
          checks.setExistDeliveredPartnerDiff(true);
        }
      }
    }
  }

  @Override
  protected void checkErrors(StringJoiner fieldErrors, DeclarationMergingResult result) {
    super.checkErrors(fieldErrors, result);

    if (appSaleService.isApp("supplychain")) {
      if (getChecks(result).isExistIncotermDiff()) {
        fieldErrors.add(I18n.get(SupplychainExceptionMessage.SALE_ORDER_MERGE_ERROR_INCOTERM));
      }
      if (getChecks(result).isExistInvoicedPartnerDiff()) {
        fieldErrors.add(
            I18n.get(SupplychainExceptionMessage.SALE_ORDER_MERGE_ERROR_INVOICED_PARTNER));
      }
      if (getChecks(result).isExistDeliveredPartnerDiff()) {
        fieldErrors.add(
            I18n.get(SupplychainExceptionMessage.SALE_ORDER_MERGE_ERROR_DELIVERED_PARTNER));
      }
    }
  }

  @Override
  protected Declaration mergeDeclarations(
      List<Declaration> declarationsToMerge, DeclarationMergingResult result) throws AxelorException {
    if (!appSaleService.isApp("supplychain")) {
      return super.mergeDeclarations(declarationsToMerge, result);
    }
    CommonFieldsSupplyChainImpl commonFields = getCommonFields(result);
    return Beans.get(DeclarationCreateServiceSupplychainImpl.class)
        .mergeDeclarations(
            declarationsToMerge,
            commonFields.getCommonCurrency(),
            commonFields.getCommonClientPartner(),
            commonFields.getCommonCompany(),
            commonFields.getCommonStockLocation(),
            commonFields.getCommonContactPartner(),
            commonFields.getCommonPriceList(),
            commonFields.getCommonTeam(),
            commonFields.getCommonTaxNumber(),
            commonFields.getCommonFiscalPosition(),
            commonFields.getCommonIncoterm(),
            commonFields.getCommonInvoicedPartner(),
            commonFields.getCommonDeliveredPartner());
  }

  @Override
  protected void updateResultWithContext(DeclarationMergingResult result, Context context) {
    super.updateResultWithContext(result, context);
    if (appSaleService.isApp("supplychain")) {
      if (context.get("stockLocation") != null) {
        getCommonFields(result)
            .setCommonStockLocation(
                MapTools.findObject(StockLocation.class, context.get("stockLocation")));
      }
    }
  }
}