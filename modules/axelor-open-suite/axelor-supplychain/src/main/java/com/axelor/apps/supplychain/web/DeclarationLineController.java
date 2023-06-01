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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Blocking;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.apps.supplychain.service.ReservedQtyService;
import com.axelor.apps.supplychain.service.DeclarationLineServiceSupplyChain;
import com.axelor.apps.supplychain.service.DeclarationLineServiceSupplyChainImpl;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class DeclarationLineController {

  public void computeAnalyticDistribution(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    if (Beans.get(AppAccountService.class).getAppAccount().getManageAnalyticAccounting()) {
      declarationLine =
          Beans.get(DeclarationLineServiceSupplyChain.class)
              .computeAnalyticDistribution(declarationLine);
      response.setValue(
          "analyticDistributionTemplate", declarationLine.getAnalyticDistributionTemplate());
      response.setValue("analyticMoveLineList", declarationLine.getAnalyticMoveLineList());
    }
  }

  public void createAnalyticDistributionWithTemplate(
      ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    declarationLine =
        Beans.get(DeclarationLineServiceSupplyChain.class)
            .createAnalyticDistributionWithTemplate(declarationLine);
    response.setValue("analyticMoveLineList", declarationLine.getAnalyticMoveLineList());
  }

  public void checkStocks(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    Declaration declaration =
        Beans.get(DeclarationLineServiceSupplyChainImpl.class).getDeclaration(request.getContext());
    if (declaration.getStockLocation() == null) {
      return;
    }
    try {
      if (declarationLine.getSaleSupplySelect() != DeclarationLineRepository.SALE_SUPPLY_FROM_STOCK) {
        return;
      }
      // Use the unit to get the right quantity
      Unit unit = null;
      if (declarationLine.getProduct() != null) unit = declarationLine.getProduct().getUnit();
      BigDecimal qty = declarationLine.getQty();
      if (unit != null && !unit.equals(declarationLine.getUnit())) {
        qty =
            Beans.get(UnitConversionService.class)
                .convert(
                    declarationLine.getUnit(), unit, qty, qty.scale(), declarationLine.getProduct());
      }
      Beans.get(StockLocationLineService.class)
          .checkIfEnoughStock(declaration.getStockLocation(), declarationLine.getProduct(), qty);
    } catch (Exception e) {
      response.setAlert(e.getLocalizedMessage());
    }
  }

  public void fillAvailableAndAllocatedStock(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    DeclarationLineServiceSupplyChainImpl declarationLineServiceSupplyChainImpl =
        Beans.get(DeclarationLineServiceSupplyChainImpl.class);
    DeclarationLine declarationLine = context.asType(DeclarationLine.class);
    Declaration declaration = declarationLineServiceSupplyChainImpl.getDeclaration(context);

    if (declaration != null) {
      if (declarationLine.getProduct() != null && declaration.getStockLocation() != null) {
        BigDecimal availableStock =
            declarationLineServiceSupplyChainImpl.getAvailableStock(declaration, declarationLine);
        BigDecimal allocatedStock =
            declarationLineServiceSupplyChainImpl.getAllocatedStock(declaration, declarationLine);

        response.setValue("$availableStock", availableStock);
        response.setValue("$allocatedStock", allocatedStock);
        response.setValue("$totalStock", availableStock.add(allocatedStock));
      }
    }
  }

  /**
   * Called from sale order line request quantity wizard view. Call {@link
   * ReservedQtyService#updateReservedQty(DeclarationLine, BigDecimal)}.
   *
   * @param request
   * @param response
   */
  public void changeReservedQty(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    BigDecimal newReservedQty = declarationLine.getReservedQty();
    try {
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      Product product = declarationLine.getProduct();
      if (product == null || !product.getStockManaged()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(SupplychainExceptionMessage.SALE_ORDER_LINE_PRODUCT_NOT_STOCK_MANAGED));
      }
      Beans.get(ReservedQtyService.class).updateReservedQty(declarationLine, newReservedQty);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void changeRequestedReservedQty(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    BigDecimal newReservedQty = declarationLine.getRequestedReservedQty();
    try {
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      Beans.get(ReservedQtyService.class).updateRequestedReservedQty(declarationLine, newReservedQty);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order line form view, on request qty click. Call {@link
   * ReservedQtyService#requestQty(DeclarationLine)}
   *
   * @param request
   * @param response
   */
  public void requestQty(ActionRequest request, ActionResponse response) {
    try {
      DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      Product product = declarationLine.getProduct();
      if (product == null || !product.getStockManaged()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(SupplychainExceptionMessage.SALE_ORDER_LINE_PRODUCT_NOT_STOCK_MANAGED));
      }
      Beans.get(ReservedQtyService.class).requestQty(declarationLine);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order line form view, on request qty click. Call {@link
   * ReservedQtyService#cancelReservation(DeclarationLine)}
   *
   * @param request
   * @param response
   */
  public void cancelReservation(ActionRequest request, ActionResponse response) {
    try {
      DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      Product product = declarationLine.getProduct();
      if (product == null || !product.getStockManaged()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(SupplychainExceptionMessage.SALE_ORDER_LINE_PRODUCT_NOT_STOCK_MANAGED));
      }
      Beans.get(ReservedQtyService.class).cancelReservation(declarationLine);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order line form. Set domain for supplier partner.
   *
   * @param request
   * @param response
   */
  public void supplierPartnerDomain(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    String domain = "self.isContact = false AND self.isSupplier = true";
    Product product = declarationLine.getProduct();
    if (product != null) {
      List<Long> authorizedPartnerIdsList =
          Beans.get(DeclarationLineServiceSupplyChain.class).getSupplierPartnerList(declarationLine);
      if (authorizedPartnerIdsList.isEmpty()) {
        response.setAttr("supplierPartner", "domain", "self.id IN (0)");
        return;
      } else {
        domain +=
            String.format(
                " AND self.id IN (%s)",
                authorizedPartnerIdsList.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(",")));
      }
    }
    Declaration declaration = declarationLine.getDeclaration();
    if (declaration == null) {
      Context parentContext = request.getContext().getParent();
      if (parentContext == null) {
        response.setAttr("supplierPartner", "domain", domain);
        return;
      }
      declaration = parentContext.asType(Declaration.class);
      if (declaration == null) {
        response.setAttr("supplierPartner", "domain", domain);
        return;
      }
    }
    String blockedPartnerQuery =
        Beans.get(BlockingService.class)
            .listOfBlockedPartner(declaration.getCompany(), BlockingRepository.PURCHASE_BLOCKING);

    if (!Strings.isNullOrEmpty(blockedPartnerQuery)) {
      domain += String.format(" AND self.id NOT in (%s)", blockedPartnerQuery);
    }

    if (declaration.getCompany() != null) {
      domain += " AND " + declaration.getCompany().getId() + " in (SELECT id FROM self.companySet)";
    }

    response.setAttr("supplierPartner", "domain", domain);
  }

  /**
   * Called from sale order line form, on product change and on sale supply select change
   *
   * @param request
   * @param response
   */
  public void supplierPartnerDefault(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    if (declarationLine.getSaleSupplySelect() != DeclarationLineRepository.SALE_SUPPLY_PURCHASE) {
      return;
    }

    Declaration declaration = declarationLine.getDeclaration();
    if (declaration == null) {
      Context parentContext = request.getContext().getParent();
      if (parentContext == null) {
        return;
      }
      declaration = parentContext.asType(Declaration.class);
    }
    if (declaration == null) {
      return;
    }

    Partner supplierPartner = null;
    if (declarationLine.getProduct() != null) {
      supplierPartner = declarationLine.getProduct().getDefaultSupplierPartner();
    }

    if (supplierPartner != null) {
      Blocking blocking =
          Beans.get(BlockingService.class)
              .getBlocking(
                  supplierPartner, declaration.getCompany(), BlockingRepository.PURCHASE_BLOCKING);
      if (blocking != null) {
        supplierPartner = null;
      }
    }

    response.setValue("supplierPartner", supplierPartner);
  }

  /**
   * Called from sale order form view, on clicking allocateAll button on one sale order line. Call
   * {@link ReservedQtyService#allocateAll(DeclarationLine)}.
   *
   * @param request
   * @param response
   */
  public void allocateAll(ActionRequest request, ActionResponse response) {
    try {
      DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      Product product = declarationLine.getProduct();
      if (product == null || !product.getStockManaged()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(SupplychainExceptionMessage.SALE_ORDER_LINE_PRODUCT_NOT_STOCK_MANAGED));
      }
      Beans.get(ReservedQtyService.class).allocateAll(declarationLine);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order form view, on clicking deallocate button on one sale order line. Call
   * {@link ReservedQtyService#updateReservedQty(DeclarationLine, BigDecimal.ZERO)}.
   *
   * @param request
   * @param response
   */
  public void deallocateAll(ActionRequest request, ActionResponse response) {
    try {
      DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      Beans.get(ReservedQtyService.class).updateReservedQty(declarationLine, BigDecimal.ZERO);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkInvoicedOrDeliveredOrderQty(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);

    DeclarationLineServiceSupplyChain declarationLineService =
        Beans.get(DeclarationLineServiceSupplyChain.class);

    BigDecimal qty = declarationLineService.checkInvoicedOrDeliveredOrderQty(declarationLine);

    declarationLineService.updateDeliveryState(declarationLine);

    response.setValue("qty", qty);
    response.setValue("deliveryState", declarationLine.getDeliveryState());
  }

  /**
   * Called from sale order line, on desired delivery date change. Call {@link
   * DeclarationLineServiceSupplyChain#updateStockMoveReservationDateTime(DeclarationLine)}.
   *
   * @param request
   * @param response
   */
  public void updateReservationDate(ActionRequest request, ActionResponse response) {
    try {
      DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      Beans.get(DeclarationLineServiceSupplyChain.class)
          .updateStockMoveReservationDateTime(declarationLine);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void getInvoicingState(ActionRequest request, ActionResponse response) {
    try {
      DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
      declarationLine = Beans.get(DeclarationLineRepository.class).find(declarationLine.getId());
      response.setValue(
          "$invoicingState",
          Beans.get(DeclarationLineServiceSupplyChain.class)
              .getDeclarationLineInvoicingState(declarationLine));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
