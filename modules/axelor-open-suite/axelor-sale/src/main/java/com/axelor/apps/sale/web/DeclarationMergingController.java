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

import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.service.declaration.DeclarationMergingService;
import com.axelor.apps.sale.service.declaration.DeclarationMergingService.DeclarationMergingResult;
import com.axelor.apps.sale.service.declaration.DeclarationMergingViewService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.utils.MapTools;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

public class DeclarationMergingController {

  public void mergeDeclaration(ActionRequest request, ActionResponse response) {

    String lineToMerge;
    if (request.getContext().get("saleQuotationToMerge") != null) {
      lineToMerge = "saleQuotationToMerge";
    } else {
      lineToMerge = "declarationToMerge";
    }
    try {
      List<Declaration> declarationsToMerge =
          MapTools.makeList(Declaration.class, request.getContext().get(lineToMerge));
      if (CollectionUtils.isNotEmpty(declarationsToMerge)) {
        DeclarationMergingResult result =
            Beans.get(DeclarationMergingService.class).mergeDeclarations(declarationsToMerge);
        if (result.isConfirmationNeeded()) {
          ActionViewBuilder confirmView =
              Beans.get(DeclarationMergingViewService.class)
                  .buildConfirmView(result, lineToMerge, declarationsToMerge);
          response.setView(confirmView.map());
          return;
        }
        if (result.getDeclaration() != null) {
          // Open the generated sale order in a new tab
          response.setView(
              ActionView.define(I18n.get("Sale order"))
                  .model(Declaration.class.getName())
                  .add("grid", "sale-order-grid")
                  .add("form", "sale-order-form")
                  .param("search-filters", "sale-order-filters")
                  .param("forceEdit", "true")
                  .context("_showRecord", String.valueOf(result.getDeclaration().getId()))
                  .map());
          response.setCanClose(true);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void mergeDeclarationFromPopUp(ActionRequest request, ActionResponse response) {
    String lineToMerge;
    if (request.getContext().get("saleQuotationToMerge") != null) {
      lineToMerge = "saleQuotationToMerge";
    } else {
      lineToMerge = "declarationToMerge";
    }
    try {
      List<Declaration> declarationsToMerge =
          MapTools.makeList(Declaration.class, request.getContext().get(lineToMerge));
      if (CollectionUtils.isNotEmpty(declarationsToMerge)) {
        DeclarationMergingResult result =
            Beans.get(DeclarationMergingService.class)
                .mergeDeclarationsWithContext(declarationsToMerge, request.getContext());
        if (result.getDeclaration() != null) {
          // Open the generated sale order in a new tab
          response.setView(
              ActionView.define(I18n.get("Sale order"))
                  .model(Declaration.class.getName())
                  .add("grid", "sale-order-grid")
                  .add("form", "sale-order-form")
                  .param("search-filters", "sale-order-filters")
                  .param("forceEdit", "true")
                  .context("_showRecord", String.valueOf(result.getDeclaration().getId()))
                  .map());
          response.setCanClose(true);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
