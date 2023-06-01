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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.ResponseMessageType;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.db.repo.PartnerSupplychainLinkTypeRepository;
import com.axelor.apps.supplychain.exception.SupplychainExceptionMessage;
import com.axelor.apps.supplychain.service.PartnerSupplychainLinkService;
import com.axelor.apps.supplychain.service.DeclarationInvoiceService;
import com.axelor.apps.supplychain.service.DeclarationLineServiceSupplyChain;
import com.axelor.apps.supplychain.service.DeclarationPurchaseService;
import com.axelor.apps.supplychain.service.DeclarationReservedQtyService;
import com.axelor.apps.supplychain.service.DeclarationServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.DeclarationStockService;
import com.axelor.apps.supplychain.service.DeclarationSupplychainService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.message.exception.MessageExceptionMessage;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class DeclarationController {

  private final String SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD = "qtyToInvoice";
  private final String SO_LINES_WIZARD_PRICE_FIELD = "price";
  private final String SO_LINES_WIZARD_QTY_FIELD = "qty";

  public void createStockMove(ActionRequest request, ActionResponse response) {

    Declaration declaration = request.getContext().asType(Declaration.class);

    try {
      if (declaration.getId() != null) {

        DeclarationStockService declarationStockService = Beans.get(DeclarationStockService.class);
        List<Long> stockMoveList =
            declarationStockService.createStocksMovesFromDeclaration(
                Beans.get(DeclarationRepository.class).find(declaration.getId()));

        if (stockMoveList != null && stockMoveList.size() == 1) {
          response.setView(
              ActionView.define(I18n.get("Stock move"))
                  .model(StockMove.class.getName())
                  .add("form", "stock-move-form")
                  .add("grid", "stock-move-grid")
                  .param("search-filters", "internal-stock-move-filters")
                  .param("forceEdit", "true")
                  .domain("self.id = " + stockMoveList.get(0))
                  .context("_showRecord", String.valueOf(stockMoveList.get(0)))
                  .context("_userType", StockMoveRepository.USER_TYPE_SALESPERSON)
                  .map());
          // we have to inject TraceBackService to use non static methods
          Beans.get(TraceBackService.class)
              .findLastMessageTraceBack(
                  Beans.get(StockMoveRepository.class).find(stockMoveList.get(0)))
              .ifPresent(
                  traceback ->
                      response.setNotify(
                          String.format(
                              I18n.get(MessageExceptionMessage.SEND_EMAIL_EXCEPTION),
                              traceback.getMessage())));
        } else if (stockMoveList != null && stockMoveList.size() > 1) {
          response.setView(
              ActionView.define(I18n.get("Stock move"))
                  .model(StockMove.class.getName())
                  .add("grid", "stock-move-grid")
                  .add("form", "stock-move-form")
                  .param("search-filters", "internal-stock-move-filters")
                  .domain("self.id in (" + Joiner.on(",").join(stockMoveList) + ")")
                  .context("_userType", StockMoveRepository.USER_TYPE_SALESPERSON)
                  .map());
          // we have to inject TraceBackService to use non static methods
          TraceBackService traceBackService = Beans.get(TraceBackService.class);
          StockMoveRepository stockMoveRepository = Beans.get(StockMoveRepository.class);

          stockMoveList.stream()
              .map(stockMoveRepository::find)
              .map(traceBackService::findLastMessageTraceBack)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .findAny()
              .ifPresent(
                  traceback ->
                      response.setNotify(
                          String.format(
                              I18n.get(MessageExceptionMessage.SEND_EMAIL_EXCEPTION),
                              traceback.getMessage())));
        } else {
          response.setInfo(
              I18n.get(SupplychainExceptionMessage.SO_NO_DELIVERY_STOCK_MOVE_TO_GENERATE));
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void getStockLocation(ActionRequest request, ActionResponse response) {

    Declaration declaration = request.getContext().asType(Declaration.class);
    try {
      Company company = declaration.getCompany();
      StockLocation stockLocation =
          Beans.get(DeclarationSupplychainService.class)
              .getStockLocation(declaration.getClientPartner(), company);
      response.setValue("stockLocation", stockLocation);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  @SuppressWarnings({"unchecked"})
  public void generatePurchaseOrdersFromSelectedSOLines(
      ActionRequest request, ActionResponse response) {

    Declaration declaration = request.getContext().asType(Declaration.class);

    try {
      if (declaration.getId() != null) {

        Partner supplierPartner = null;
        List<Long> declarationLineIdSelected;
        Boolean isDirectOrderLocation = false;
        Boolean noProduct = true;
        Map<String, Object> values = getSelectedId(request, response, declaration);
        supplierPartner = (Partner) values.get("supplierPartner");
        declarationLineIdSelected = (List<Long>) values.get("declarationLineIdSelected");
        isDirectOrderLocation = (Boolean) values.get("isDirectOrderLocation");

        if (supplierPartner == null) {
          declarationLineIdSelected = new ArrayList<>();
          for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
            if (declarationLine.isSelected()) {
              if (supplierPartner == null) {
                supplierPartner = declarationLine.getSupplierPartner();
              }
              if (declarationLine.getProduct() != null) {
                noProduct = false;
              }
              declarationLineIdSelected.add(declarationLine.getId());
            }
          }

          if (declarationLineIdSelected.isEmpty() || noProduct) {
            response.setInfo(I18n.get(SupplychainExceptionMessage.SO_LINE_PURCHASE_AT_LEAST_ONE));
          } else {
            response.setView(
                ActionView.define(I18n.get("Declaration"))
                    .model(Declaration.class.getName())
                    .add("form", "sale-order-generate-po-select-supplierpartner-form")
                    .param("popup", "true")
                    .param("show-toolbar", "false")
                    .param("show-confirm", "false")
                    .param("popup-save", "false")
                    .param("forceEdit", "true")
                    .context("_showRecord", String.valueOf(declaration.getId()))
                    .context(
                        "supplierPartnerId",
                        ((supplierPartner != null) ? supplierPartner.getId() : 0L))
                    .context(
                        "declarationLineIdSelected", Joiner.on(",").join(declarationLineIdSelected))
                    .map());
          }
        } else {
          List<DeclarationLine> declarationLinesSelected =
              JPA.all(DeclarationLine.class)
                  .filter("self.id IN (:saleOderLineIdList)")
                  .bind("saleOderLineIdList", declarationLineIdSelected)
                  .fetch();
          PurchaseOrder purchaseOrder =
              Beans.get(DeclarationPurchaseService.class)
                  .createPurchaseOrder(
                      supplierPartner,
                      declarationLinesSelected,
                      Beans.get(DeclarationRepository.class).find(declaration.getId()));
          response.setView(
              ActionView.define(I18n.get("Purchase order"))
                  .model(PurchaseOrder.class.getName())
                  .add("form", "purchase-order-form")
                  .param("forceEdit", "true")
                  .context("_showRecord", String.valueOf(purchaseOrder.getId()))
                  .map());

          if (isDirectOrderLocation == false) {
            response.setCanClose(true);
          }
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  @SuppressWarnings("rawtypes")
  private Map<String, Object> getSelectedId(
      ActionRequest request, ActionResponse response, Declaration declaration) throws AxelorException {
    Partner supplierPartner = null;
    List<Long> declarationLineIdSelected = new ArrayList<>();
    Map<String, Object> values = new HashMap<>();
    Boolean isDirectOrderLocation = false;
    Boolean noProduct = true;

    if (declaration.getDirectOrderLocation()
        && declaration.getStockLocation() != null
        && declaration.getStockLocation().getPartner() != null
        && declaration.getStockLocation().getPartner().getIsSupplier()) {
      values.put("supplierPartner", declaration.getStockLocation().getPartner());

      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
        if (declarationLine.isSelected()) {
          if (declarationLine.getProduct() != null) {
            noProduct = false;
          }
          declarationLineIdSelected.add(declarationLine.getId());
        }
      }
      values.put("declarationLineIdSelected", declarationLineIdSelected);
      isDirectOrderLocation = true;
      values.put("isDirectOrderLocation", isDirectOrderLocation);

      if (declarationLineIdSelected.isEmpty() || noProduct) {
        throw new AxelorException(
            3, I18n.get(SupplychainExceptionMessage.SO_LINE_PURCHASE_AT_LEAST_ONE));
      }
    } else if (request.getContext().get("supplierPartnerSelect") != null) {
      supplierPartner =
          JPA.em()
              .find(
                  Partner.class,
                  Long.valueOf(
                      (Integer)
                          ((Map) request.getContext().get("supplierPartnerSelect")).get("id")));
      values.put("supplierPartner", supplierPartner);
      String declarationLineIdSelectedStr =
          (String) request.getContext().get("declarationLineIdSelected");

      for (String declarationId : declarationLineIdSelectedStr.split(",")) {
        declarationLineIdSelected.add(Long.valueOf(declarationId));
      }
      values.put("declarationLineIdSelected", declarationLineIdSelected);
      values.put("isDirectOrderLocation", isDirectOrderLocation);
    }

    return values;
  }

  /**
   * Called from the sale order invoicing wizard. Call {@link
   * com.axelor.apps.supplychain.service.DeclarationInvoiceService#generateInvoice(Declaration, int,
   * BigDecimal, boolean, Map)} } Return to the view the generated invoice.
   *
   * @param request
   * @param response
   */
  @SuppressWarnings(value = "unchecked")
  public void generateInvoice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    try {
      Declaration declaration = context.asType(Declaration.class);
      int operationSelect = Integer.parseInt(context.get("operationSelect").toString());
      boolean isPercent = (Boolean) context.getOrDefault("isPercent", false);
      BigDecimal amountToInvoice =
          new BigDecimal(context.getOrDefault("amountToInvoice", "0").toString());

      DeclarationInvoiceService declarationInvoiceService = Beans.get(DeclarationInvoiceService.class);

      Map<Long, BigDecimal> qtyMap = new HashMap<>();
      Map<Long, BigDecimal> qtyToInvoiceMap = new HashMap<>();
      Map<Long, BigDecimal> priceMap = new HashMap<>();

      List<Map<String, Object>> declarationLineListContext;
      declarationLineListContext =
          (List<Map<String, Object>>) request.getRawContext().get("declarationLineList");
      for (Map<String, Object> map : declarationLineListContext) {
        if (map.get(SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD) != null) {
          BigDecimal qtyToInvoiceItem =
              new BigDecimal(map.get(SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD).toString());
          if (qtyToInvoiceItem.compareTo(BigDecimal.ZERO) != 0) {
            Long soLineId = Long.valueOf((Integer) map.get("id"));
            qtyToInvoiceMap.put(soLineId, qtyToInvoiceItem);
            BigDecimal priceItem = new BigDecimal(map.get(SO_LINES_WIZARD_PRICE_FIELD).toString());
            priceMap.put(soLineId, priceItem);
            BigDecimal qtyItem = new BigDecimal(map.get(SO_LINES_WIZARD_QTY_FIELD).toString());
            qtyMap.put(soLineId, qtyItem);
          }
        }
      }

      // Re-compute amount to invoice if invoicing partially
      amountToInvoice =
          declarationInvoiceService.computeAmountToInvoice(
              amountToInvoice,
              operationSelect,
              declaration,
              qtyToInvoiceMap,
              priceMap,
              qtyMap,
              isPercent);

      declarationInvoiceService.displayErrorMessageIfDeclarationIsInvoiceable(
          declaration, amountToInvoice, isPercent);

      // Information to send to the service to handle an invoicing on timetables
      List<Long> timetableIdList = new ArrayList<>();
      ArrayList<LinkedHashMap<String, Object>> uninvoicedTimetablesList =
          (context.get("uninvoicedTimetablesList") != null)
              ? (ArrayList<LinkedHashMap<String, Object>>) context.get("uninvoicedTimetablesList")
              : null;
      if (uninvoicedTimetablesList != null && !uninvoicedTimetablesList.isEmpty()) {

        for (LinkedHashMap<String, Object> timetable : uninvoicedTimetablesList) {
          if (timetable.get("toInvoice") != null && (boolean) timetable.get("toInvoice")) {
            timetableIdList.add(Long.parseLong(timetable.get("id").toString()));
          }
        }
      }

      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());

      Invoice invoice =
          declarationInvoiceService.generateInvoice(
              declaration,
              operationSelect,
              amountToInvoice,
              isPercent,
              qtyToInvoiceMap,
              timetableIdList);

      if (invoice != null) {
        response.setCanClose(true);
        response.setView(
            ActionView.define(I18n.get("Invoice generated"))
                .model(Invoice.class.getName())
                .add("form", "invoice-form")
                .add("grid", "invoice-grid")
                .param("search-filters", "customer-invoices-filters")
                .context("_showRecord", String.valueOf(invoice.getId()))
                .context("_operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
                .context(
                    "todayDate",
                    Beans.get(AppSupplychainService.class).getTodayDate(declaration.getCompany()))
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void updateAmountToBeSpreadOverTheTimetable(
      ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    Beans.get(DeclarationSupplychainService.class).updateAmountToBeSpreadOverTheTimetable(declaration);
    response.setValue(
        "amountToBeSpreadOverTheTimetable", declaration.getAmountToBeSpreadOverTheTimetable());
  }

  /**
   * Called from sale order on save. Call {@link
   * DeclarationServiceSupplychainImpl#checkModifiedConfirmedOrder(Declaration, Declaration)}.
   *
   * @param request
   * @param response
   */
  public void onSave(ActionRequest request, ActionResponse response) {
    try {
      Declaration declarationView = request.getContext().asType(Declaration.class);
      if (declarationView.getOrderBeingEdited()) {
        Declaration declaration = Beans.get(DeclarationRepository.class).find(declarationView.getId());
        Beans.get(DeclarationServiceSupplychainImpl.class)
            .checkModifiedConfirmedOrder(declaration, declarationView);
        response.setValues(declarationView);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  /**
   * Called on sale order invoicing wizard form. Call {@link
   * DeclarationInvoiceService#getInvoicingWizardOperationDomain(Declaration)}
   *
   * @param request
   * @param response
   */
  public void changeWizardOperationDomain(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    List<Integer> operationSelectValues =
        Beans.get(DeclarationInvoiceService.class).getInvoicingWizardOperationDomain(declaration);
    response.setAttr(
        "$operationSelect",
        "value",
        operationSelectValues.stream().min(Integer::compareTo).orElse(null));

    response.setAttr("$operationSelect", "selection-in", operationSelectValues);
  }

  /**
   * Called from sale order generate purchase order form. Set domain for supplier partner.
   *
   * @param request
   * @param response
   */
  public void supplierPartnerSelectDomain(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    String domain = "self.isContact = false AND self.isSupplier = true";

    String blockedPartnerQuery =
        Beans.get(BlockingService.class)
            .listOfBlockedPartner(declaration.getCompany(), BlockingRepository.PURCHASE_BLOCKING);

    if (!Strings.isNullOrEmpty(blockedPartnerQuery)) {
      domain += String.format(" AND self.id NOT in (%s)", blockedPartnerQuery);
    }

    if (declaration.getCompany() != null) {
      domain += " AND " + declaration.getCompany().getId() + " in (SELECT id FROM self.companySet)";
    }
    response.setAttr("supplierPartnerSelect", "domain", domain);
  }

  public void setNextInvoicingStartPeriodDate(ActionRequest request, ActionResponse response) {

    Declaration declaration = request.getContext().asType(Declaration.class);

    TemporalUnit temporalUnit = ChronoUnit.MONTHS;

    if (declaration.getPeriodicityTypeSelect() != null
        && declaration.getNextInvoicingStartPeriodDate() != null) {
      LocalDate invoicingPeriodStartDate = declaration.getNextInvoicingStartPeriodDate();
      if (declaration.getPeriodicityTypeSelect() == 1) {
        temporalUnit = ChronoUnit.DAYS;
      }
      LocalDate subscriptionToDate =
          invoicingPeriodStartDate.plus(declaration.getNumberOfPeriods(), temporalUnit);
      subscriptionToDate = subscriptionToDate.minusDays(1);
      response.setValue("nextInvoicingEndPeriodDate", subscriptionToDate);
    }
  }

  /**
   * Called on load of sale order invoicing wizard view. Fill dummy field with default value to
   * avoid issues with null values.
   *
   * @param request
   * @param response
   */
  public void fillDefaultValueWizard(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      List<Map<String, Object>> declarationLineList = new ArrayList<>();
      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
        Map<String, Object> declarationLineMap = Mapper.toMap(declarationLine);
        declarationLineMap.put(SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD, BigDecimal.ZERO);
        declarationLineList.add(declarationLineMap);
      }
      response.setValue("$amountToInvoice", BigDecimal.ZERO);
      response.setValue("declarationLineList", declarationLineList);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void fillDeclarationLinesEstimatedDate(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);

    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (declarationLineList != null) {
      for (DeclarationLine declarationLine : declarationLineList) {
        Integer deliveryState = declarationLine.getDeliveryState();
        if (!deliveryState.equals(DeclarationLineRepository.DELIVERY_STATE_DELIVERED)
            && !deliveryState.equals(DeclarationLineRepository.DELIVERY_STATE_PARTIALLY_DELIVERED)) {
          declarationLine.setEstimatedShippingDate(declaration.getEstimatedShippingDate());
        }
      }
    }

    response.setValue("declarationLineList", declarationLineList);
  }

  public void fillDeclarationLinesDeliveryDate(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);

    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (declarationLineList != null) {
      for (DeclarationLine declarationLine : declarationLineList) {
        declarationLine.setEstimatedDeliveryDate(declaration.getEstimatedDeliveryDate());
      }
    }

    response.setValue("declarationLineList", declarationLineList);
  }

  public void notifyStockMoveCreated(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    StockMoveRepository stockMoveRepo = Beans.get(StockMoveRepository.class);
    StockMove stockMove =
        stockMoveRepo
            .all()
            .filter(
                "self.originTypeSelect = ?1 AND self.originId = ?2 AND self.statusSelect = ?3",
                "com.axelor.apps.sale.db.Declaration",
                declaration.getId(),
                StockMoveRepository.STATUS_PLANNED)
            .fetchOne();
    if (stockMove != null) {
      response.setNotify(
          String.format(
              I18n.get(SupplychainExceptionMessage.SALE_ORDER_STOCK_MOVE_CREATED),
              stockMove.getStockMoveSeq()));
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.DeclarationReservedQtyService#allocateAll(Declaration)}.
   *
   * @param request
   * @param response
   */
  public void allocateAll(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      Beans.get(DeclarationReservedQtyService.class).allocateAll(declaration);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.DeclarationReservedQtyService#deallocateAll(Declaration)}.
   *
   * @param request
   * @param response
   */
  public void deallocateAll(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      Beans.get(DeclarationReservedQtyService.class).deallocateAll(declaration);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.DeclarationReservedQtyService#reserveAll(Declaration)}.
   *
   * @param request
   * @param response
   */
  public void reserveAll(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      Beans.get(DeclarationReservedQtyService.class).reserveAll(declaration);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.DeclarationReservedQtyService#cancelReservation(Declaration)}.
   *
   * @param request
   * @param response
   */
  public void cancelReservation(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      Beans.get(DeclarationReservedQtyService.class).cancelReservation(declaration);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void showPopUpInvoicingWizard(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      Beans.get(DeclarationInvoiceService.class).displayErrorMessageBtnGenerateInvoice(declaration);
      response.setView(
          ActionView.define(I18n.get("Invoicing"))
              .model(Declaration.class.getName())
              .add("form", "sale-order-invoicing-wizard-form")
              .param("popup", "reload")
              .param("show-toolbar", "false")
              .param("show-confirm", "false")
              .param("popup-save", "false")
              .param("forceEdit", "true")
              .context("_showRecord", String.valueOf(declaration.getId()))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void generateAdvancePaymentInvoice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    try {
      Declaration declaration = context.asType(Declaration.class);
      Beans.get(DeclarationInvoiceService.class).displayErrorMessageBtnGenerateInvoice(declaration);
      Boolean isPercent = (Boolean) context.getOrDefault("isPercent", false);
      BigDecimal amountToInvoice =
          new BigDecimal(context.getOrDefault("amountToInvoice", "0").toString());
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());

      Invoice invoice =
          Beans.get(DeclarationInvoiceService.class)
              .generateInvoice(
                  declaration,
                  DeclarationRepository.INVOICE_ADVANCE_PAYMENT,
                  amountToInvoice,
                  isPercent,
                  null,
                  null);

      if (invoice != null) {
        response.setCanClose(true);
        response.setView(
            ActionView.define(I18n.get("Invoice generated"))
                .model(Invoice.class.getName())
                .add("form", "invoice-form")
                .add("grid", "invoice-grid")
                .param("search-filters", "customer-invoices-filters")
                .context("_showRecord", String.valueOf(invoice.getId()))
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void backToConfirmedStatus(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      Beans.get(DeclarationSupplychainService.class).updateToConfirmedStatus(declaration);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void createShipmentCostLine(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      String message =
          Beans.get(DeclarationSupplychainService.class).createShipmentCostLine(declaration);
      if (message != null) {
        response.setInfo(message);
      }
      response.setValues(declaration);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
  /**
   * Called from sale order form view, on invoiced partner select. Call {@link
   * PartnerSupplychainLinkService#computePartnerFilter}
   *
   * @param request
   * @param response
   */
  public void setInvoicedPartnerDomain(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      String strFilter =
          Beans.get(PartnerSupplychainLinkService.class)
              .computePartnerFilter(
                  declaration.getClientPartner(),
                  PartnerSupplychainLinkTypeRepository.TYPE_SELECT_INVOICED_BY);

      response.setAttr("invoicedPartner", "domain", strFilter);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order form view, on delivered partner select. Call {@link
   * PartnerSupplychainLinkService#computePartnerFilter}
   *
   * @param request
   * @param response
   */
  public void setDeliveredPartnerDomain(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      String strFilter =
          Beans.get(PartnerSupplychainLinkService.class)
              .computePartnerFilter(
                  declaration.getClientPartner(),
                  PartnerSupplychainLinkTypeRepository.TYPE_SELECT_DELIVERED_BY);

      response.setAttr("deliveredPartner", "domain", strFilter);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from sale order view, on delivery date change. <br>
   * Update stock reservation date for each sale order line by calling {@link
   * DeclarationLineServiceSupplyChain#updateStockMoveReservationDateTime(DeclarationLine)}.
   *
   * @param request
   * @param response
   */
  public void updateStockReservationDate(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      DeclarationLineServiceSupplyChain declarationLineServiceSupplyChain =
          Beans.get(DeclarationLineServiceSupplyChain.class);
      for (DeclarationLine declarationLine : declaration.getDeclarationLineList()) {
        declarationLineServiceSupplyChain.updateStockMoveReservationDateTime(declarationLine);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void setDefaultInvoicedAndDeliveredPartnersAndAddresses(
      ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      Beans.get(DeclarationSupplychainService.class)
          .setDefaultInvoicedAndDeliveredPartnersAndAddresses(declaration);
      response.setValues(declaration);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void getToStockLocation(ActionRequest request, ActionResponse response) {
    Declaration declaration = request.getContext().asType(Declaration.class);
    try {
      Company company = declaration.getCompany();
      StockLocation toStockLocation =
          Beans.get(DeclarationSupplychainService.class)
              .getToStockLocation(declaration.getClientPartner(), company);
      response.setValue("toStockLocation", toStockLocation);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void setInvoicingState(ActionRequest request, ActionResponse response) {
    try {
      Declaration declaration = request.getContext().asType(Declaration.class);
      declaration = Beans.get(DeclarationRepository.class).find(declaration.getId());
      response.setValue(
          "$invoicingState",
          Beans.get(DeclarationInvoiceService.class).getDeclarationInvoicingState(declaration));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
