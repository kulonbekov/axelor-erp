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
package com.axelor.apps.sale.db.repo;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationComputeService;
import com.axelor.apps.sale.service.declaration.DeclarationMarginService;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.inject.Beans;
import com.axelor.studio.db.AppSale;
import com.google.common.base.Strings;
import java.math.BigDecimal;
import java.util.List;
import javax.persistence.PersistenceException;

public class DeclarationManagementRepository extends DeclarationRepository {

  @Override
  public Declaration copy(Declaration entity, boolean deep) {

    Declaration copy = super.copy(entity, deep);

    copy.setStatusSelect(DeclarationRepository.STATUS_DRAFT_QUOTATION);
    copy.setDeclarationSeq(null);
    copy.clearBatchSet();
    copy.setImportId(null);
    copy.setCreationDate(Beans.get(AppBaseService.class).getTodayDate(entity.getCompany()));
    copy.setConfirmationDateTime(null);
    copy.setConfirmedByUser(null);
    copy.setOrderDate(null);
    copy.setOrderNumber(null);
    copy.setVersionNumber(1);
    copy.setTotalCostPrice(null);
    copy.setTotalGrossMargin(null);
    copy.setMarginRate(null);
    copy.setEndOfValidityDate(null);
    copy.setEstimatedShippingDate(null);
    copy.setOrderBeingEdited(false);
    if (copy.getAdvancePaymentAmountNeeded().compareTo(copy.getAdvanceTotal()) <= 0) {
      copy.setAdvancePaymentAmountNeeded(BigDecimal.ZERO);
      copy.setAdvancePaymentNeeded(false);
      copy.clearAdvancePaymentList();
    }

    if (copy.getDeclarationLineList() != null) {
      for (DeclarationLine declarationLine : copy.getDeclarationLineList()) {
        declarationLine.setDesiredDeliveryDate(null);
        declarationLine.setEstimatedShippingDate(null);
        declarationLine.setDiscountDerogation(null);
      }
    }

    return copy;
  }

  @Override
  public Declaration save(Declaration declaration) {
    try {
      AppSale appSale = Beans.get(AppSaleService.class).getAppSale();
      DeclarationComputeService declarationComputeService = Beans.get(DeclarationComputeService.class);

      if (appSale.getEnablePackManagement()) {
        declarationComputeService.computePackTotal(declaration);
      } else {
        declarationComputeService.resetPackTotal(declaration);
      }
      computeSeq(declaration);
      computeFullName(declaration);

      if (appSale.getManagePartnerComplementaryProduct()) {
        Beans.get(DeclarationService.class).manageComplementaryProductSOLines(declaration);
      }

      computeSubMargin(declaration);
      Beans.get(DeclarationMarginService.class).computeMarginDeclaration(declaration);
      return super.save(declaration);
    } catch (Exception e) {
      TraceBackService.traceExceptionFromSaveMethod(e);
      throw new PersistenceException(e.getMessage(), e);
    }
  }

  public void computeSeq(Declaration declaration) {
    try {
      if (declaration.getId() == null) {
        declaration = super.save(declaration);
      }
      if (Strings.isNullOrEmpty(declaration.getDeclarationSeq()) && !declaration.getTemplate()) {
        if (declaration.getStatusSelect() == DeclarationRepository.STATUS_DRAFT_QUOTATION) {
          declaration.setDeclarationSeq(
              Beans.get(SequenceService.class).getDraftSequenceNumber(declaration));
        }
      }

    } catch (Exception e) {
      throw new PersistenceException(e.getMessage(), e);
    }
  }

  public void computeFullName(Declaration declaration) {
    try {
      if (declaration.getClientPartner() != null) {
        String fullName = declaration.getClientPartner().getName();
        if (!Strings.isNullOrEmpty(declaration.getDeclarationSeq())) {
          fullName = declaration.getDeclarationSeq() + "-" + fullName;
        }
        declaration.setFullName(fullName);
      }
    } catch (Exception e) {
      throw new PersistenceException(e.getMessage(), e);
    }
  }

  protected void computeSubMargin(Declaration declaration) throws AxelorException {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (declarationLineList != null) {
      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
        Beans.get(DeclarationMarginService.class).computeSubMargin(declaration, declarationLine);
      }
    }
  }
}
