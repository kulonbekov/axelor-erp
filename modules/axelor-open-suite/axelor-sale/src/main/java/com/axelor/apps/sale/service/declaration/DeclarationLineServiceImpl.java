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
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Pricing;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCategoryService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.ProductMultipleQtyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.base.service.pricing.PricingComputer;
import com.axelor.apps.base.service.pricing.PricingObserver;
import com.axelor.apps.base.service.pricing.PricingService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.apps.sale.db.ComplementaryProduct;
import com.axelor.apps.sale.db.ComplementaryProductSelected;
import com.axelor.apps.sale.db.Pack;
import com.axelor.apps.sale.db.PackLine;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.ComplementaryProductRepository;
import com.axelor.apps.sale.db.repo.PackLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.declaration.pricing.DeclarationLinePricingObserver;
import com.axelor.apps.sale.translation.ITranslation;
import com.axelor.common.ObjectUtils;
import com.axelor.db.EntityHelper;
import com.axelor.i18n.I18n;
import com.axelor.meta.loader.ModuleManager;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationLineServiceImpl implements DeclarationLineService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected CurrencyService currencyService;
  protected PriceListService priceListService;
  protected ProductMultipleQtyService productMultipleQtyService;
  protected AppBaseService appBaseService;
  protected AppSaleService appSaleService;
  protected AccountManagementService accountManagementService;
  protected DeclarationLineRepository declarationLineRepo;
  protected DeclarationService declarationService;
  protected PricingService pricingService;
  protected TaxService taxService;
  protected DeclarationMarginService declarationMarginService;

  @Inject
  public DeclarationLineServiceImpl(
      CurrencyService currencyService,
      PriceListService priceListService,
      ProductMultipleQtyService productMultipleQtyService,
      AppBaseService appBaseService,
      AppSaleService appSaleService,
      AccountManagementService accountManagementService,
      DeclarationLineRepository declarationLineRepo,
      DeclarationService declarationService,
      PricingService pricingService,
      TaxService taxService,
      DeclarationMarginService declarationMarginService) {
    this.currencyService = currencyService;
    this.priceListService = priceListService;
    this.productMultipleQtyService = productMultipleQtyService;
    this.appBaseService = appBaseService;
    this.appSaleService = appSaleService;
    this.accountManagementService = accountManagementService;
    this.declarationLineRepo = declarationLineRepo;
    this.declarationService = declarationService;
    this.pricingService = pricingService;
    this.taxService = taxService;
    this.declarationMarginService = declarationMarginService;
  }

  @Inject protected ProductCategoryService productCategoryService;
  @Inject protected ProductCompanyService productCompanyService;

  @Override
  public void computeProductInformation(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException {

    // Reset fields which are going to recalculate in this method
    resetProductInformation(declarationLine);

    if (!declarationLine.getEnableFreezeFields()) {
      declarationLine.setProductName(declarationLine.getProduct().getName());
    }
    declarationLine.setUnit(this.getSaleUnit(declarationLine));
    if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
      declarationLine.setDescription(declarationLine.getProduct().getDescription());
    }

    declarationLine.setTypeSelect(DeclarationLineRepository.TYPE_NORMAL);
    fillPrice(declarationLine, declaration);
    fillComplementaryProductList(declarationLine);
  }

  @Override
  public void computePricingScale(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException {

    Optional<Pricing> pricing = getRootPricing(declarationLine, declaration);
    if (pricing.isPresent() && declarationLine.getProduct() != null) {
      PricingComputer pricingComputer =
          getPricingComputer(pricing.get(), declarationLine)
              .putInContext("declaration", EntityHelper.getEntity(declaration));
      pricingComputer.subscribe(getDeclarationLinePricingObserver(declarationLine));
      pricingComputer.apply();
    } else {
      declarationLine.setPricingScaleLogs(I18n.get(ITranslation.SALE_ORDER_LINE_OBSERVER_NO_PRICING));
    }
  }

  protected PricingObserver getDeclarationLinePricingObserver(DeclarationLine declarationLine) {
    return new DeclarationLinePricingObserver(declarationLine);
  }

  protected PricingComputer getPricingComputer(Pricing pricing, DeclarationLine declarationLine)
      throws AxelorException {

    return PricingComputer.of(
        pricing, declarationLine, declarationLine.getProduct(), DeclarationLine.class);
  }

  protected Optional<Pricing> getRootPricing(DeclarationLine declarationLine, Declaration declaration) {
    // It is supposed that only one pricing match those criteria (because of the configuration)
    // Having more than one pricing matched may result on a unexpected result
    return pricingService.getRandomPricing(
        declaration.getCompany(),
        declarationLine.getProduct(),
        declarationLine.getProduct() != null ? declarationLine.getProduct().getProductCategory() : null,
        DeclarationLine.class.getSimpleName(),
        null);
  }

  @Override
  public boolean hasPricingLine(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException {

    Optional<Pricing> pricing = getRootPricing(declarationLine, declaration);
    if (pricing.isPresent()) {
      return !getPricingComputer(pricing.get(), declarationLine)
          .putInContext("declaration", EntityHelper.getEntity(declaration))
          .getMatchedPricingLines()
          .isEmpty();
    }

    return false;
  }

  @Override
  public void fillPrice(DeclarationLine declarationLine, Declaration declaration) throws AxelorException {

    // Populate fields from pricing scale before starting process of fillPrice
    if (appSaleService.getAppSale().getEnablePricingScale()) {
      computePricingScale(declarationLine, declaration);
    }

    fillTaxInformation(declarationLine, declaration);
    declarationLine.setCompanyCostPrice(this.getCompanyCostPrice(declaration, declarationLine));
    BigDecimal exTaxPrice;
    BigDecimal inTaxPrice;
    if (declarationLine.getProduct().getInAti()) {
      inTaxPrice = this.getInTaxUnitPrice(declaration, declarationLine, declarationLine.getTaxLine());
      inTaxPrice = fillDiscount(declarationLine, declaration, inTaxPrice);
      if (!declarationLine.getEnableFreezeFields()) {
        declarationLine.setPrice(
            taxService.convertUnitPrice(
                true,
                declarationLine.getTaxLine(),
                inTaxPrice,
                appBaseService.getNbDecimalDigitForUnitPrice()));
        declarationLine.setInTaxPrice(inTaxPrice);
      }
    } else {
      exTaxPrice = this.getExTaxUnitPrice(declaration, declarationLine, declarationLine.getTaxLine());
      exTaxPrice = fillDiscount(declarationLine, declaration, exTaxPrice);
      if (!declarationLine.getEnableFreezeFields()) {
        declarationLine.setPrice(exTaxPrice);
        declarationLine.setInTaxPrice(
            taxService.convertUnitPrice(
                false,
                declarationLine.getTaxLine(),
                exTaxPrice,
                appBaseService.getNbDecimalDigitForUnitPrice()));
      }
    }
  }

  @Override
  public void fillComplementaryProductList(DeclarationLine declarationLine) {
    if (declarationLine.getProduct() != null
        && declarationLine.getProduct().getComplementaryProductList() != null) {
      if (declarationLine.getSelectedComplementaryProductList() == null) {
        declarationLine.setSelectedComplementaryProductList(new ArrayList<>());
      }
      declarationLine.clearSelectedComplementaryProductList();
      for (ComplementaryProduct complProduct :
          declarationLine.getProduct().getComplementaryProductList()) {
        ComplementaryProductSelected newComplProductLine = new ComplementaryProductSelected();

        newComplProductLine.setProduct(complProduct.getProduct());
        newComplProductLine.setQty(complProduct.getQty());
        newComplProductLine.setOptional(complProduct.getOptional());

        newComplProductLine.setIsSelected(!complProduct.getOptional());
        newComplProductLine.setDeclarationLine(declarationLine);
        declarationLine.addSelectedComplementaryProductListItem(newComplProductLine);
      }
    }
  }

  protected BigDecimal fillDiscount(
      DeclarationLine declarationLine, Declaration declaration, BigDecimal price) {

    Map<String, Object> discounts =
        this.getDiscountsFromPriceLists(declaration, declarationLine, price);

    if (discounts != null) {
      if (discounts.get("price") != null) {
        price = (BigDecimal) discounts.get("price");
      }
      if (declarationLine.getProduct().getInAti() != declaration.getInAti()
          && (Integer) discounts.get("discountTypeSelect")
              != PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
        declarationLine.setDiscountAmount(
            taxService.convertUnitPrice(
                declarationLine.getProduct().getInAti(),
                declarationLine.getTaxLine(),
                (BigDecimal) discounts.get("discountAmount"),
                appBaseService.getNbDecimalDigitForUnitPrice()));
      } else {
        declarationLine.setDiscountAmount((BigDecimal) discounts.get("discountAmount"));
      }
      declarationLine.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));
    } else if (!declaration.getTemplate()) {
      declarationLine.setDiscountAmount(BigDecimal.ZERO);
      declarationLine.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    }

    return price;
  }

  protected void fillTaxInformation(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException {

    if (declaration.getClientPartner() != null) {
      TaxLine taxLine = this.getTaxLine(declaration, declarationLine);
      declarationLine.setTaxLine(taxLine);

      FiscalPosition fiscalPosition = declaration.getFiscalPosition();

      TaxEquiv taxEquiv =
          accountManagementService.getProductTaxEquiv(
              declarationLine.getProduct(), declaration.getCompany(), fiscalPosition, false);

      declarationLine.setTaxEquiv(taxEquiv);
    } else {
      declarationLine.setTaxLine(null);
      declarationLine.setTaxEquiv(null);
    }
  }

  @Override
  public DeclarationLine resetProductInformation(DeclarationLine line) {
    if (!line.getEnableFreezeFields()) {
      line.setProductName(null);
      line.setPrice(null);
    }
    line.setTaxLine(null);
    line.setTaxEquiv(null);
    line.setUnit(null);
    line.setCompanyCostPrice(null);
    line.setDiscountAmount(null);
    line.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    line.setInTaxPrice(null);
    line.setExTaxTotal(null);
    line.setInTaxTotal(null);
    line.setCompanyInTaxTotal(null);
    line.setCompanyExTaxTotal(null);
    if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
      line.setDescription(null);
    }
    line.clearSelectedComplementaryProductList();
    return line;
  }

  @Override
  public void resetPrice(DeclarationLine line) {
    if (!line.getEnableFreezeFields()) {
      line.setPrice(null);
      line.setInTaxPrice(null);
    }
  }

  @Override
  public Map<String, BigDecimal> computeValues(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException {

    HashMap<String, BigDecimal> map = new HashMap<>();
    if (declaration == null
        || declarationLine.getPrice() == null
        || declarationLine.getInTaxPrice() == null
        || declarationLine.getQty() == null) {
      return map;
    }

    BigDecimal exTaxTotal;
    BigDecimal companyExTaxTotal;
    BigDecimal inTaxTotal;
    BigDecimal companyInTaxTotal;
    BigDecimal priceDiscounted = this.computeDiscount(declarationLine, declaration.getInAti());
    BigDecimal taxRate = BigDecimal.ZERO;
    BigDecimal subTotalCostPrice = BigDecimal.ZERO;

    if (declarationLine.getTaxLine() != null) {
      taxRate = declarationLine.getTaxLine().getValue().divide(new BigDecimal(100));
    }

    if (!declaration.getInAti()) {
      exTaxTotal = this.computeAmount(declarationLine.getQty(), priceDiscounted);
      inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
      companyExTaxTotal = this.getAmountInCompanyCurrency(exTaxTotal, declaration);
      companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(taxRate));
    } else {
      inTaxTotal = this.computeAmount(declarationLine.getQty(), priceDiscounted);
      exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
      companyInTaxTotal = this.getAmountInCompanyCurrency(inTaxTotal, declaration);
      companyExTaxTotal =
          companyInTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
    }

    if (declarationLine.getProduct() != null
        && ((BigDecimal)
                    productCompanyService.get(
                        declarationLine.getProduct(), "costPrice", declaration.getCompany()))
                .compareTo(BigDecimal.ZERO)
            != 0) {
      subTotalCostPrice =
          ((BigDecimal)
                  productCompanyService.get(
                      declarationLine.getProduct(), "costPrice", declaration.getCompany()))
              .multiply(declarationLine.getQty());
    }

    declarationLine.setInTaxTotal(inTaxTotal);
    declarationLine.setExTaxTotal(exTaxTotal);
    declarationLine.setPriceDiscounted(priceDiscounted);
    declarationLine.setCompanyInTaxTotal(companyInTaxTotal);
    declarationLine.setCompanyExTaxTotal(companyExTaxTotal);
    declarationLine.setSubTotalCostPrice(subTotalCostPrice);
    map.put("inTaxTotal", inTaxTotal);
    map.put("exTaxTotal", exTaxTotal);
    map.put("priceDiscounted", priceDiscounted);
    map.put("companyExTaxTotal", companyExTaxTotal);
    map.put("companyInTaxTotal", companyInTaxTotal);
    map.put("subTotalCostPrice", subTotalCostPrice);

    map.putAll(declarationMarginService.getDeclarationLineComputedMarginInfo(declaration, declarationLine));

    return map;
  }

  /**
   * Compute the excluded tax total amount of a sale order line.
   *
   * @return The excluded tax total amount.
   */
  @Override
  public BigDecimal computeAmount(DeclarationLine declarationLine) {

    BigDecimal price = this.computeDiscount(declarationLine, false);

    return computeAmount(declarationLine.getQty(), price);
  }

  @Override
  public BigDecimal computeAmount(BigDecimal quantity, BigDecimal price) {

    BigDecimal amount =
        quantity
            .multiply(price)
            .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);

    logger.debug(
        "Computation of W.T. amount with a quantity of {} for {} : {}",
        new Object[] {quantity, price, amount});

    return amount;
  }

  @Override
  public BigDecimal getExTaxUnitPrice(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPrice(declaration, declarationLine, taxLine, false);
  }

  @Override
  public BigDecimal getInTaxUnitPrice(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPrice(declaration, declarationLine, taxLine, true);
  }

  /**
   * A function used to get the unit price of a sale order line, either in ati or wt
   *
   * @param declaration the sale order containing the sale order line
   * @param declarationLine
   * @param taxLine the tax applied to the unit price
   * @param resultInAti whether you want the result in ati or not
   * @return the unit price of the sale order line
   * @throws AxelorException
   */
  protected BigDecimal getUnitPrice(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine, boolean resultInAti)
      throws AxelorException {
    Product product = declarationLine.getProduct();

    Boolean productInAti =
        (Boolean) productCompanyService.get(product, "inAti", declaration.getCompany());

    // Consider price if already computed from pricing scale else get it from product
    BigDecimal productSalePrice = declarationLine.getPrice();

    if (productSalePrice.compareTo(BigDecimal.ZERO) == 0) {
      productSalePrice =
          (BigDecimal) productCompanyService.get(product, "salePrice", declaration.getCompany());
    }

    BigDecimal price =
        (productInAti == resultInAti)
            ? productSalePrice
            : taxService.convertUnitPrice(
                productInAti, taxLine, productSalePrice, AppBaseService.COMPUTATION_SCALING);

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            (Currency) productCompanyService.get(product, "saleCurrency", declaration.getCompany()),
            declaration.getCurrency(),
            price,
            declaration.getCreationDate())
        .setScale(appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
  }

  @Override
  public TaxLine getTaxLine(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException {

    return accountManagementService.getTaxLine(
        declaration.getCreationDate(),
        declarationLine.getProduct(),
        declaration.getCompany(),
        declaration.getFiscalPosition(),
        false);
  }

  @Override
  public BigDecimal getAmountInCompanyCurrency(BigDecimal exTaxTotal, Declaration declaration)
      throws AxelorException {

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            declaration.getCurrency(),
            declaration.getCompany().getCurrency(),
            exTaxTotal,
            declaration.getCreationDate())
        .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public BigDecimal getCompanyCostPrice(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException {

    Product product = declarationLine.getProduct();

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            (Currency)
                productCompanyService.get(product, "purchaseCurrency", declaration.getCompany()),
            declaration.getCompany().getCurrency(),
            (BigDecimal) productCompanyService.get(product, "costPrice", declaration.getCompany()),
            declaration.getCreationDate())
        .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public PriceListLine getPriceListLine(
      DeclarationLine declarationLine, PriceList priceList, BigDecimal price) {

    return priceListService.getPriceListLine(
        declarationLine.getProduct(), declarationLine.getQty(), priceList, price);
  }

  @Override
  public BigDecimal computeDiscount(DeclarationLine declarationLine, Boolean inAti) {

    BigDecimal price = inAti ? declarationLine.getInTaxPrice() : declarationLine.getPrice();

    return priceListService.computeDiscount(
        price, declarationLine.getDiscountTypeSelect(), declarationLine.getDiscountAmount());
  }

  @Override
  public Map<String, Object> getDiscountsFromPriceLists(
      Declaration declaration, DeclarationLine declarationLine, BigDecimal price) {

    Map<String, Object> discounts = null;

    PriceList priceList = declaration.getPriceList();

    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(declarationLine, priceList, price);
      discounts = priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, price);

      if (declaration.getTemplate()) {
        Integer manualDiscountAmountType = declarationLine.getDiscountTypeSelect();
        BigDecimal manualDiscountAmount = declarationLine.getDiscountAmount();
        Integer priceListDiscountAmountType = (Integer) discounts.get("discountTypeSelect");
        BigDecimal priceListDiscountAmount = (BigDecimal) discounts.get("discountAmount");

        if (!manualDiscountAmountType.equals(priceListDiscountAmountType)
            && manualDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_PERCENT)
            && priceListDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_FIXED)) {
          priceListDiscountAmount =
              priceListDiscountAmount
                  .multiply(new BigDecimal(100))
                  .divide(price, 2, RoundingMode.HALF_UP);
        } else if (!manualDiscountAmountType.equals(priceListDiscountAmountType)
            && manualDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_FIXED)
            && priceListDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_PERCENT)) {
          priceListDiscountAmount =
              priceListDiscountAmount
                  .multiply(price)
                  .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        }

        if (manualDiscountAmount.compareTo(priceListDiscountAmount) > 0) {
          discounts.put("discountAmount", manualDiscountAmount);
          discounts.put("discountTypeSelect", manualDiscountAmountType);
        }
      }
    }

    return discounts;
  }

  @Override
  public int getDiscountTypeSelect(
      Declaration declaration, DeclarationLine declarationLine, BigDecimal price) {
    PriceList priceList = declaration.getPriceList();
    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(declarationLine, priceList, price);

      return priceListLine.getTypeSelect();
    }
    return 0;
  }

  @Override
  public Unit getSaleUnit(DeclarationLine declarationLine) {
    Unit unit = declarationLine.getProduct().getSalesUnit();
    if (unit == null) {
      unit = declarationLine.getProduct().getUnit();
    }
    return unit;
  }

  @Override
  public Declaration getDeclaration(Context context) {

    Context parentContext = context.getParent();

    DeclarationLine declarationLine = context.asType(DeclarationLine.class);
    Declaration declaration = declarationLine.getDeclaration();

    if (parentContext != null && !parentContext.getContextClass().equals(Declaration.class)) {
      parentContext = parentContext.getParent();
    }

    if (parentContext != null && parentContext.getContextClass().equals(Declaration.class)) {
      declaration = parentContext.asType(Declaration.class);
    }

    return declaration;
  }

  @Override
  public BigDecimal getAvailableStock(Declaration declaration, DeclarationLine declarationLine) {
    // defined in supplychain
    return BigDecimal.ZERO;
  }

  @Override
  public BigDecimal getAllocatedStock(Declaration declaration, DeclarationLine declarationLine) {
    // defined in supplychain
    return BigDecimal.ZERO;
  }

  @Override
  public void checkMultipleQty(DeclarationLine declarationLine, ActionResponse response) {

    Product product = declarationLine.getProduct();

    if (product == null) {
      return;
    }

    productMultipleQtyService.checkMultipleQty(
        declarationLine.getQty(),
        product.getSaleProductMultipleQtyList(),
        product.getAllowToForceSaleQty(),
        response);
  }

  @Override
  public DeclarationLine createDeclarationLine(
      PackLine packLine,
      Declaration declaration,
      BigDecimal packQty,
      BigDecimal conversionRate,
      Integer sequence)
      throws AxelorException {

    if (packLine.getTypeSelect() == PackLineRepository.TYPE_START_OF_PACK
        || packLine.getTypeSelect() == PackLineRepository.TYPE_END_OF_PACK) {
      return createStartOfPackAndEndOfPackTypeDeclarationLine(
          packLine.getPack(), declaration, packQty, packLine, packLine.getTypeSelect(), sequence);
    }

    if (packLine.getProductName() != null) {
      DeclarationLine soLine = new DeclarationLine();

      Product product = packLine.getProduct();
      soLine.setProduct(product);
      soLine.setProductName(packLine.getProductName());
      if (packLine.getQuantity() != null) {
        soLine.setQty(
            packLine
                .getQuantity()
                .multiply(packQty)
                .setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_UP));
      }
      soLine.setUnit(packLine.getUnit());
      soLine.setTypeSelect(packLine.getTypeSelect());
      soLine.setSequence(sequence);
      if (packLine.getPrice() != null) {
        soLine.setPrice(packLine.getPrice().multiply(conversionRate));
      }

      if (product != null) {
        if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
          soLine.setDescription(product.getDescription());
        }
        try {
          this.fillPriceFromPackLine(soLine, declaration);
          this.computeValues(declaration, soLine);
        } catch (AxelorException e) {
          TraceBackService.trace(e);
        }
      }
      return soLine;
    }
    return null;
  }

  @Override
  public BigDecimal computeMaxDiscount(Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException {
    Optional<BigDecimal> maxDiscount = Optional.empty();
    Product product = declarationLine.getProduct();
    if (product != null && product.getProductCategory() != null) {
      maxDiscount = productCategoryService.computeMaxDiscount(product.getProductCategory());
    }
    if (!maxDiscount.isPresent()
        || declarationLine.getDiscountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_NONE
        || declaration == null
        || (declaration.getStatusSelect() != DeclarationRepository.STATUS_DRAFT_QUOTATION
            && (declaration.getStatusSelect() != DeclarationRepository.STATUS_ORDER_CONFIRMED
                || !declaration.getOrderBeingEdited()))) {
      return null;
    } else {
      return maxDiscount.get();
    }
  }

  @Override
  public boolean isDeclarationLineDiscountGreaterThanMaxDiscount(
      DeclarationLine declarationLine, BigDecimal maxDiscount) {
    return (declarationLine.getDiscountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_PERCENT
            && declarationLine.getDiscountAmount().compareTo(maxDiscount) > 0)
        || (declarationLine.getDiscountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_FIXED
            && declarationLine.getPrice().signum() != 0
            && declarationLine
                    .getDiscountAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(declarationLine.getPrice(), 2, RoundingMode.HALF_UP)
                    .compareTo(maxDiscount)
                > 0);
  }

  @Override
  public List<DeclarationLine> createNonStandardSOLineFromPack(
      Pack pack,
      Declaration declaration,
      BigDecimal packQty,
      List<DeclarationLine> declarationLineList,
      Integer sequence) {
    DeclarationLine declarationLine;
    Set<Integer> packLineTypeSet = getPackLineTypes(pack.getComponents());
    int typeSelect = DeclarationLineRepository.TYPE_START_OF_PACK;
    for (int i = 0; i < 2; i++) {
      if (packLineTypeSet == null || !packLineTypeSet.contains(typeSelect)) {
        declarationLine =
            this.createStartOfPackAndEndOfPackTypeDeclarationLine(
                pack, declaration, packQty, null, typeSelect, sequence);
        declarationLineList.add(declarationLine);
      }
      if (typeSelect == DeclarationLineRepository.TYPE_START_OF_PACK) {
        sequence += pack.getComponents().size() + 1;
        typeSelect = DeclarationLineRepository.TYPE_END_OF_PACK;
      }
    }

    return declarationLineList;
  }

  @Override
  public DeclarationLine createStartOfPackAndEndOfPackTypeDeclarationLine(
      Pack pack,
      Declaration declaration,
      BigDecimal packqty,
      PackLine packLine,
      Integer typeSelect,
      Integer sequence) {

    DeclarationLine declarationLine = new DeclarationLine();
    declarationLine.setTypeSelect(typeSelect);
    switch (typeSelect) {
      case DeclarationLineRepository.TYPE_START_OF_PACK:
        declarationLine.setProductName(packLine == null ? pack.getName() : packLine.getProductName());
        declarationLine.setQty(
            packLine != null && packLine.getQuantity() != null
                ? packLine
                    .getQuantity()
                    .multiply(packqty)
                    .setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_EVEN)
                : packqty);
        break;

      case DeclarationLineRepository.TYPE_END_OF_PACK:
        declarationLine.setProductName(
            packLine == null
                ? I18n.get(ITranslation.SALE_ORDER_LINE_END_OF_PACK)
                : packLine.getProductName());
        declarationLine.setIsShowTotal(pack.getIsShowTotal());
        declarationLine.setIsHideUnitAmounts(pack.getIsHideUnitAmounts());
        break;
      default:
        return null;
    }
    declarationLine.setDeclaration(declaration);
    declarationLine.setSequence(sequence);
    return declarationLine;
  }

  @Override
  public boolean hasEndOfPackTypeLine(List<DeclarationLine> declarationLineList) {
    return ObjectUtils.isEmpty(declarationLineList)
        ? Boolean.FALSE
        : declarationLineList.stream()
            .anyMatch(
                declarationLine ->
                    declarationLine.getTypeSelect() == DeclarationLineRepository.TYPE_END_OF_PACK);
  }

  @Override
  public DeclarationLine updateProductQty(
      DeclarationLine declarationLine, Declaration declaration, BigDecimal oldQty, BigDecimal newQty)
      throws AxelorException {
    if (declarationLine.getTypeSelect() != DeclarationLineRepository.TYPE_NORMAL) {
      return declarationLine;
    }
    this.fillPriceFromPackLine(declarationLine, declaration);
    this.computeValues(declaration, declarationLine);
    return declarationLine;
  }

  @Override
  public boolean isStartOfPackTypeLineQtyChanged(List<DeclarationLine> declarationLineList) {

    if (ObjectUtils.isEmpty(declarationLineList)) {
      return false;
    }
    for (DeclarationLine declarationLine : declarationLineList) {
      if (declarationLine.getTypeSelect() == DeclarationLineRepository.TYPE_START_OF_PACK
          && declarationLine.getId() != null) {
        DeclarationLine oldDeclarationLine = declarationLineRepo.find(declarationLine.getId());
        if (oldDeclarationLine.getTypeSelect() == DeclarationLineRepository.TYPE_START_OF_PACK
            && declarationLine.getQty().compareTo(oldDeclarationLine.getQty()) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void fillPriceFromPackLine(DeclarationLine declarationLine, Declaration declaration)
      throws AxelorException {
    this.fillTaxInformation(declarationLine, declaration);
    declarationLine.setCompanyCostPrice(this.getCompanyCostPrice(declaration, declarationLine));
    BigDecimal exTaxPrice;
    BigDecimal inTaxPrice;
    if (declarationLine.getProduct().getInAti()) {
      inTaxPrice =
          this.getInTaxUnitPriceFromPackLine(declaration, declarationLine, declarationLine.getTaxLine());
      inTaxPrice = fillDiscount(declarationLine, declaration, inTaxPrice);
      if (!declarationLine.getEnableFreezeFields()) {
        declarationLine.setPrice(
            taxService.convertUnitPrice(
                true,
                declarationLine.getTaxLine(),
                inTaxPrice,
                appBaseService.getNbDecimalDigitForUnitPrice()));
        declarationLine.setInTaxPrice(inTaxPrice);
      }
    } else {
      exTaxPrice =
          this.getExTaxUnitPriceFromPackLine(declaration, declarationLine, declarationLine.getTaxLine());
      exTaxPrice = fillDiscount(declarationLine, declaration, exTaxPrice);
      if (!declarationLine.getEnableFreezeFields()) {
        declarationLine.setPrice(exTaxPrice);
        declarationLine.setInTaxPrice(
            taxService.convertUnitPrice(
                false,
                declarationLine.getTaxLine(),
                exTaxPrice,
                appBaseService.getNbDecimalDigitForUnitPrice()));
      }
    }
  }

  @Override
  public BigDecimal getExTaxUnitPriceFromPackLine(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPriceFromPackLine(declaration, declarationLine, taxLine, false);
  }

  @Override
  public BigDecimal getInTaxUnitPriceFromPackLine(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPriceFromPackLine(declaration, declarationLine, taxLine, true);
  }

  /**
   * A method used to get the unit price of a sale order line from pack line, either in ati or wt
   *
   * @param declaration the sale order containing the sale order line
   * @param declarationLine
   * @param taxLine the tax applied to the unit price
   * @param resultInAti whether you want the result in ati or not
   * @return the unit price of the sale order line
   * @throws AxelorException
   */
  protected BigDecimal getUnitPriceFromPackLine(
      Declaration declaration, DeclarationLine declarationLine, TaxLine taxLine, boolean resultInAti)
      throws AxelorException {

    Product product = declarationLine.getProduct();

    Boolean productInAti =
        (Boolean) productCompanyService.get(product, "inAti", declaration.getCompany());
    BigDecimal productSalePrice = declarationLine.getPrice();

    BigDecimal price =
        (productInAti == resultInAti)
            ? productSalePrice
            : taxService.convertUnitPrice(
                productInAti, taxLine, productSalePrice, AppBaseService.COMPUTATION_SCALING);

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            (Currency) productCompanyService.get(product, "saleCurrency", declaration.getCompany()),
            declaration.getCurrency(),
            price,
            declaration.getCreationDate())
        .setScale(appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
  }

  @Override
  public Set<Integer> getPackLineTypes(List<PackLine> packLineList) {
    Set<Integer> packLineTypeSet = new HashSet<>();
    packLineList.stream()
        .forEach(
            packLine -> {
              if (packLine.getTypeSelect() == PackLineRepository.TYPE_START_OF_PACK) {
                packLineTypeSet.add(PackLineRepository.TYPE_START_OF_PACK);
              } else if (packLine.getTypeSelect() == PackLineRepository.TYPE_END_OF_PACK) {
                packLineTypeSet.add(PackLineRepository.TYPE_END_OF_PACK);
              }
            });
    return packLineTypeSet;
  }

  @Override
  public String computeProductDomain(DeclarationLine declarationLine, Declaration declaration) {
    String domain =
        "self.isModel = false"
            + " and (self.endDate = null or self.endDate > :__date__)"
            + " and self.dtype = 'Product'";

    if (appBaseService.getAppBase().getCompanySpecificProductFieldsSet() != null
        && appBaseService.getAppBase().getCompanySpecificProductFieldsSet().stream()
            .anyMatch(it -> "sellable".equals(it.getName()))
        && declaration != null
        && declaration.getCompany() != null) {
      domain +=
          " and (SELECT sellable "
              + "FROM ProductCompany productCompany "
              + "WHERE productCompany.product.id = self.id "
              + "AND productCompany.company.id = "
              + declaration.getCompany().getId()
              + ") IS TRUE ";
    } else {
      domain += " and self.sellable = true ";
    }

    if (appSaleService.getAppSale().getEnableSalesProductByTradName()
        && declaration != null
        && declaration.getTradingName() != null
        && declaration.getCompany() != null
        && declaration.getCompany().getTradingNameSet() != null
        && !declaration.getCompany().getTradingNameSet().isEmpty()) {
      domain +=
          " AND " + declaration.getTradingName().getId() + " member of self.tradingNameSellerSet";
    }

    // The standard way to do this would be to override the method in HR module.
    // But here, we have to do this because overriding a sale service in hr module will prevent the
    // override in supplychain, business-project, and business production module.
    if (ModuleManager.isInstalled("axelor-human-resource")) {
      domain += " AND self.expense = false ";
    }

    return domain;
  }

  @Override
  public List<DeclarationLine> manageComplementaryProductDeclarationLine(
      ComplementaryProduct complementaryProduct, Declaration declaration, DeclarationLine declarationLine)
      throws AxelorException {

    List<DeclarationLine> newComplementarySOLines = new ArrayList<>();
    if (declarationLine.getMainDeclarationLine() != null) {
      return newComplementarySOLines;
    }

    if (declarationLine.getComplementaryDeclarationLineList() == null) {
      declarationLine.setComplementaryDeclarationLineList(new ArrayList<>());
    }

    DeclarationLine complementarySOLine =
        getOrCreateComplementryLine(
            complementaryProduct.getProduct(), declarationLine, newComplementarySOLines);

    complementarySOLine.setQty(complementaryProduct.getQty());
    complementarySOLine.setIsComplementaryPartnerProductsHandled(
        complementaryProduct.getGenerationTypeSelect()
            == ComplementaryProductRepository.GENERATION_TYPE_SALE_ORDER);
    this.computeProductInformation(complementarySOLine, declaration);
    this.computeValues(declaration, complementarySOLine);
    declarationLineRepo.save(complementarySOLine);
    return newComplementarySOLines;
  }

  protected DeclarationLine getOrCreateComplementryLine(
      Product product, DeclarationLine declarationLine, List<DeclarationLine> newComplementarySOLines) {
    DeclarationLine complementarySOLine;
    Optional<DeclarationLine> complementarySOLineOpt =
        declarationLine.getComplementaryDeclarationLineList().stream()
            .filter(
                line -> line.getMainDeclarationLine() != null && line.getProduct().equals(product))
            .findFirst();
    if (complementarySOLineOpt.isPresent()) {
      complementarySOLine = complementarySOLineOpt.get();
    } else {
      complementarySOLine = new DeclarationLine();
      complementarySOLine.setSequence(declarationLine.getSequence());
      complementarySOLine.setProduct(product);
      complementarySOLine.setMainDeclarationLine(declarationLine);
      newComplementarySOLines.add(complementarySOLine);
    }
    return complementarySOLine;
  }

  public List<DeclarationLine> updateLinesAfterFiscalPositionChange(Declaration declaration)
      throws AxelorException {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();

    if (CollectionUtils.isEmpty(declarationLineList)) {
      return null;
    }

    for (DeclarationLine declarationLine : declarationLineList) {

      // Skip line update if product is not filled
      if (declarationLine.getProduct() == null) {
        continue;
      }

      FiscalPosition fiscalPosition = declaration.getFiscalPosition();
      TaxLine taxLine = this.getTaxLine(declaration, declarationLine);
      declarationLine.setTaxLine(taxLine);

      TaxEquiv taxEquiv =
          accountManagementService.getProductTaxEquiv(
              declarationLine.getProduct(), declaration.getCompany(), fiscalPosition, false);

      declarationLine.setTaxEquiv(taxEquiv);

      BigDecimal exTaxTotal = declarationLine.getExTaxTotal();

      BigDecimal companyExTaxTotal = declarationLine.getCompanyExTaxTotal();

      BigDecimal salePrice =
          (BigDecimal)
              productCompanyService.get(
                  declarationLine.getProduct(), "salePrice", declaration.getCompany());

      declarationLine.setInTaxTotal(
          taxService.convertUnitPrice(
              false, taxLine, exTaxTotal, appBaseService.getNbDecimalDigitForUnitPrice()));
      declarationLine.setCompanyInTaxTotal(
          taxService.convertUnitPrice(
              false, taxLine, companyExTaxTotal, appBaseService.getNbDecimalDigitForUnitPrice()));
      declarationLine.setInTaxPrice(
          taxService.convertUnitPrice(
              false, taxLine, salePrice, appBaseService.getNbDecimalDigitForUnitPrice()));
    }
    return declarationLineList;
  }
}
