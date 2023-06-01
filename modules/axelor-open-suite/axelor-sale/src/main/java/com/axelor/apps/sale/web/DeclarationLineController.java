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

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Pricing;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.InternationalService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.base.service.pricing.PricingService;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.exception.SaleExceptionMessage;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.DeclarationLineService;
import com.axelor.apps.sale.service.declaration.DeclarationMarginService;
import com.axelor.apps.sale.translation.ITranslation;
import com.axelor.auth.AuthUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Singleton
public class DeclarationLineController {

  public void compute(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    DeclarationLine declarationLine = context.asType(DeclarationLine.class);

    Declaration declaration = Beans.get(DeclarationLineService.class).getDeclaration(context);

    try {
      compute(response, declaration, declarationLine);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void computeSubMargin(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Context context = request.getContext();
    DeclarationLine declarationLine = context.asType(DeclarationLine.class);
    Declaration declaration = Beans.get(DeclarationLineService.class).getDeclaration(context);
    Map<String, BigDecimal> map =
        Beans.get(DeclarationMarginService.class)
            .getDeclarationLineComputedMarginInfo(declaration, declarationLine);

    response.setValues(map);
  }

  /**
   * Called by the sale order line form. Update all fields when the product is changed.
   *
   * @param request
   * @param response
   */
  public void getProductInformation(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      DeclarationLine declarationLine = context.asType(DeclarationLine.class);
      DeclarationLineService declarationLineService = Beans.get(DeclarationLineService.class);
      PricingService pricingService = Beans.get(PricingService.class);
      Declaration declaration = declarationLineService.getDeclaration(context);

      Product product = declarationLine.getProduct();

      if (declaration == null || product == null) {
        resetProductInformation(response, declarationLine);
        return;
      }

      try {
        product = Beans.get(ProductRepository.class).find(product.getId());
        declarationLineService.computeProductInformation(declarationLine, declaration);

        if (Beans.get(AppSaleService.class).getAppSale().getEnablePricingScale()) {
          Optional<Pricing> defaultPricing =
              pricingService.getRandomPricing(
                  declaration.getCompany(),
                  declarationLine.getProduct(),
                  declarationLine.getProduct().getProductCategory(),
                  DeclarationLine.class.getSimpleName(),
                  null);

          if (defaultPricing.isPresent()
              && !declarationLineService.hasPricingLine(declarationLine, declaration)) {
            response.setInfo(
                String.format(
                    I18n.get(SaleExceptionMessage.SALE_ORDER_LINE_PRICING_NOT_APPLIED),
                    defaultPricing.get().getName()));
          }
        }

        response.setValue("saleSupplySelect", product.getSaleSupplySelect());
        response.setValues(declarationLine);
      } catch (Exception e) {
        resetProductInformation(response, declarationLine);
        TraceBackService.trace(response, e);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void resetProductInformation(ActionResponse response, DeclarationLine line) {
    Beans.get(DeclarationLineService.class).resetProductInformation(line);
    response.setValue("saleSupplySelect", null);
    response.setValue("typeSelect", DeclarationLineRepository.TYPE_NORMAL);
    response.setValues(line);
  }

  public void getTaxEquiv(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    DeclarationLine declarationLine = context.asType(DeclarationLine.class);
    Declaration declaration = Beans.get(DeclarationLineService.class).getDeclaration(context);

    response.setValue("taxEquiv", null);

    if (declaration == null
        || declarationLine == null
        || declaration.getClientPartner() == null
        || declarationLine.getTaxLine() == null) return;

    response.setValue(
        "taxEquiv",
        Beans.get(FiscalPositionService.class)
            .getTaxEquiv(declaration.getFiscalPosition(), declarationLine.getTaxLine().getTax()));
  }

  public void getDiscount(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    DeclarationLine declarationLine = context.asType(DeclarationLine.class);
    DeclarationLineService declarationLineService = Beans.get(DeclarationLineService.class);
    TaxService taxService = Beans.get(TaxService.class);
    AppBaseService appBaseService = Beans.get(AppBaseService.class);

    Declaration declaration = declarationLineService.getDeclaration(context);

    if (declaration == null || declarationLine.getProduct() == null) {
      return;
    }

    try {

      Map<String, Object> discounts;
      if (declarationLine.getProduct().getInAti()) {
        discounts =
            declarationLineService.getDiscountsFromPriceLists(
                declaration,
                declarationLine,
                declarationLineService.getInTaxUnitPrice(
                    declaration, declarationLine, declarationLine.getTaxLine()));
      } else {
        discounts =
            declarationLineService.getDiscountsFromPriceLists(
                declaration,
                declarationLine,
                declarationLineService.getExTaxUnitPrice(
                    declaration, declarationLine, declarationLine.getTaxLine()));
      }

      if (discounts != null) {
        BigDecimal price = (BigDecimal) discounts.get("price");
        if (price != null
            && price.compareTo(
                    declarationLine.getProduct().getInAti()
                        ? declarationLine.getInTaxPrice()
                        : declarationLine.getPrice())
                != 0) {
          if (declarationLine.getProduct().getInAti()) {
            response.setValue("inTaxPrice", price);
            response.setValue(
                "price",
                taxService.convertUnitPrice(
                    true,
                    declarationLine.getTaxLine(),
                    price,
                    appBaseService.getNbDecimalDigitForUnitPrice()));
          } else {
            response.setValue("price", price);
            response.setValue(
                "inTaxPrice",
                taxService.convertUnitPrice(
                    false,
                    declarationLine.getTaxLine(),
                    price,
                    appBaseService.getNbDecimalDigitForUnitPrice()));
          }
        }

        if (declarationLine.getProduct().getInAti() != declaration.getInAti()
            && (Integer) discounts.get("discountTypeSelect")
                != PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
          response.setValue(
              "discountAmount",
              taxService.convertUnitPrice(
                  declarationLine.getProduct().getInAti(),
                  declarationLine.getTaxLine(),
                  (BigDecimal) discounts.get("discountAmount"),
                  appBaseService.getNbDecimalDigitForUnitPrice()));
        } else {
          response.setValue("discountAmount", discounts.get("discountAmount"));
        }
        response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
      }

    } catch (Exception e) {
      response.setInfo(e.getMessage());
    }
  }

  /**
   * Update the ex. tax unit price of an invoice line from its in. tax unit price.
   *
   * @param request
   * @param response
   */
  public void updatePrice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    DeclarationLine declarationLine = context.asType(DeclarationLine.class);

    try {
      BigDecimal inTaxPrice = declarationLine.getInTaxPrice();
      TaxLine taxLine = declarationLine.getTaxLine();

      response.setValue(
          "price",
          Beans.get(TaxService.class)
              .convertUnitPrice(
                  true,
                  taxLine,
                  inTaxPrice,
                  Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice()));
    } catch (Exception e) {
      response.setInfo(e.getMessage());
    }
  }

  /**
   * Update the in. tax unit price of an invoice line from its ex. tax unit price.
   *
   * @param request
   * @param response
   */
  public void updateInTaxPrice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    DeclarationLine declarationLine = context.asType(DeclarationLine.class);

    try {
      BigDecimal exTaxPrice = declarationLine.getPrice();
      TaxLine taxLine = declarationLine.getTaxLine();

      response.setValue(
          "inTaxPrice",
          Beans.get(TaxService.class)
              .convertUnitPrice(
                  false,
                  taxLine,
                  exTaxPrice,
                  Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice()));
    } catch (Exception e) {
      response.setInfo(e.getMessage());
    }
  }

  public void convertUnitPrice(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    DeclarationLine declarationLine = context.asType(DeclarationLine.class);

    Declaration declaration = Beans.get(DeclarationLineService.class).getDeclaration(context);

    if (declaration == null
        || declarationLine.getProduct() == null
        || declarationLine.getPrice() == null
        || declarationLine.getInTaxPrice() == null) {
      return;
    }

    try {

      BigDecimal price = declarationLine.getPrice();
      BigDecimal inTaxPrice =
          price.add(
              price.multiply(declarationLine.getTaxLine().getValue().divide(new BigDecimal(100))));

      response.setValue("inTaxPrice", inTaxPrice);

    } catch (Exception e) {
      response.setInfo(e.getMessage());
    }
  }

  public void emptyLine(ActionRequest request, ActionResponse response) {
    DeclarationLine declarationLine = request.getContext().asType(DeclarationLine.class);
    if (declarationLine.getTypeSelect() != DeclarationLineRepository.TYPE_NORMAL) {
      Map<String, Object> newDeclarationLine = Mapper.toMap(new DeclarationLine());
      newDeclarationLine.put("qty", BigDecimal.ZERO);
      newDeclarationLine.put("id", declarationLine.getId());
      newDeclarationLine.put("version", declarationLine.getVersion());
      newDeclarationLine.put("typeSelect", declarationLine.getTypeSelect());
      if (declarationLine.getTypeSelect() == DeclarationLineRepository.TYPE_END_OF_PACK) {
        newDeclarationLine.put("productName", I18n.get(ITranslation.SALE_ORDER_LINE_END_OF_PACK));
      }
      response.setValues(newDeclarationLine);
    }
  }

  public void checkQty(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    DeclarationLine declarationLine = context.asType(DeclarationLine.class);
    Beans.get(DeclarationLineService.class).checkMultipleQty(declarationLine, response);
  }

  /**
   * Called from sale order line form view on load and on discount type select change. Call {@link
   * DeclarationLineService#computeMaxDiscount} and set the message to the view.
   *
   * @param request
   * @param response
   */
  public void fillMaxDiscount(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      DeclarationLine declarationLine = context.asType(DeclarationLine.class);
      DeclarationLineService declarationLineService = Beans.get(DeclarationLineService.class);
      Declaration declaration = declarationLineService.getDeclaration(context);
      BigDecimal maxDiscount = declarationLineService.computeMaxDiscount(declaration, declarationLine);
      response.setValue("$maxDiscount", maxDiscount);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  protected void compute(ActionResponse response, Declaration declaration, DeclarationLine orderLine)
      throws AxelorException {

    Map<String, BigDecimal> map =
        Beans.get(DeclarationLineService.class).computeValues(declaration, orderLine);

    map.put("price", orderLine.getPrice());
    map.put("inTaxPrice", orderLine.getInTaxPrice());
    map.put("companyCostPrice", orderLine.getCompanyCostPrice());
    map.put("discountAmount", orderLine.getDiscountAmount());

    response.setValues(map);
    response.setAttr(
        "priceDiscounted",
        "hidden",
        map.getOrDefault("priceDiscounted", BigDecimal.ZERO)
                .compareTo(declaration.getInAti() ? orderLine.getInTaxPrice() : orderLine.getPrice())
            == 0);
  }

  /**
   * Called from sale order line form view, on product selection. Call {@link
   * DeclarationLineService#computeProductDomain(DeclarationLine, Declaration)}.
   *
   * @param request
   * @param response
   */
  public void computeProductDomain(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      DeclarationLine declarationLine = context.asType(DeclarationLine.class);
      DeclarationLineService declarationLineService = Beans.get(DeclarationLineService.class);
      Declaration declaration = declarationLineService.getDeclaration(context);
      response.setAttr(
          "product", "domain", declarationLineService.computeProductDomain(declarationLine, declaration));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void computePricingScale(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      DeclarationLine declarationLine = context.asType(DeclarationLine.class);
      DeclarationLineService declarationLineService = Beans.get(DeclarationLineService.class);
      Declaration declaration = declarationLineService.getDeclaration(context);
      Beans.get(DeclarationLineService.class).computePricingScale(declarationLine, declaration);

      response.setValues(declarationLine);

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void translateProductDescriptionAndName(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      InternationalService internationalService = Beans.get(InternationalService.class);
      DeclarationLine declarationLine = context.asType(DeclarationLine.class);
      Partner partner =
          Beans.get(DeclarationLineService.class).getDeclaration(context).getClientPartner();
      String userLanguage = AuthUtils.getUser().getLanguage();
      Product product = declarationLine.getProduct();

      if (product != null) {
        Map<String, String> translation =
            internationalService.getProductDescriptionAndNameTranslation(
                product, partner, userLanguage);

        String description = translation.get("description");
        String productName = translation.get("productName");

        if (description != null
            && !description.isEmpty()
            && productName != null
            && !productName.isEmpty()) {
          response.setValue("description", description);
          response.setValue("productName", productName);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
