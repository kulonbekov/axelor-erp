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

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.base.service.DurationService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.currency.CurrencyConversionFactory;
import com.axelor.apps.base.service.exception.TraceBackService;
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
import com.axelor.apps.sale.exception.SaleExceptionMessage;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.JpaSequence;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import wslite.json.JSONException;

public class DeclarationServiceImpl implements DeclarationService {

  protected DeclarationLineService declarationLineService;
  protected AppBaseService appBaseService;
  protected DeclarationLineRepository declarationLineRepo;
  protected DeclarationRepository declarationRepo;
  protected DeclarationComputeService declarationComputeService;
  protected DeclarationMarginService declarationMarginService;

  @Inject
  public DeclarationServiceImpl(
      DeclarationLineService declarationLineService,
      AppBaseService appBaseService,
      DeclarationLineRepository declarationLineRepo,
      DeclarationRepository declarationRepo,
      DeclarationComputeService declarationComputeService,
      DeclarationMarginService declarationMarginService) {
    this.declarationLineService = declarationLineService;
    this.appBaseService = appBaseService;
    this.declarationLineRepo = declarationLineRepo;
    this.declarationRepo = declarationRepo;
    this.declarationComputeService = declarationComputeService;
    this.declarationMarginService = declarationMarginService;
  }

  @Override
  public String getFileName(Declaration declaration) {
    String prefixFileName = I18n.get("Sale order");
    if (declaration.getStatusSelect() == DeclarationRepository.STATUS_DRAFT_QUOTATION
        || declaration.getStatusSelect() == DeclarationRepository.STATUS_FINALIZED_QUOTATION) {
      prefixFileName = I18n.get("Sale quotation");
    }
    return prefixFileName
        + " "
        + declaration.getDeclarationSeq()
        + ((Beans.get(AppSaleService.class).getAppSale().getManageDeclarationVersion()
                && declaration.getVersionNumber() > 1)
            ? "-V" + declaration.getVersionNumber()
            : "");
  }

  @Override
  public Declaration computeEndOfValidityDate(Declaration declaration) {
    Company company = declaration.getCompany();
    if (declaration.getDuration() == null && company != null && company.getSaleConfig() != null) {
      declaration.setDuration(company.getSaleConfig().getDefaultValidityDuration());
    }
    if (declaration.getCreationDate() != null) {
      declaration.setEndOfValidityDate(
          Beans.get(DurationService.class)
              .computeDuration(declaration.getDuration(), declaration.getCreationDate()));
    }
    return declaration;
  }

  @Override
  public void computeAddressStr(Declaration declaration) {
    AddressService addressService = Beans.get(AddressService.class);
    declaration.setMainInvoicingAddressStr(
        addressService.computeAddressStr(declaration.getMainInvoicingAddress()));
    declaration.setDeliveryAddressStr(
        addressService.computeAddressStr(declaration.getDeliveryAddress()));
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public boolean enableEditOrder(Declaration declaration) throws AxelorException {
    if (declaration.getStatusSelect() == DeclarationRepository.STATUS_ORDER_COMPLETED) {
      throw new AxelorException(
          declaration,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(SaleExceptionMessage.SALES_ORDER_COMPLETED));
    }

    declaration.setOrderBeingEdited(true);
    return false;
  }

  @Override
  public void checkModifiedConfirmedOrder(Declaration declaration, Declaration declarationView)
      throws AxelorException {
    // Nothing to check if we don't have supplychain.
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validateChanges(Declaration declaration) throws AxelorException {
    checkUnauthorizedDiscounts(declaration);
  }

  @Override
  public void sortDeclarationLineList(Declaration declaration) {
    if (declaration.getDeclarationLineList() != null) {
      declaration.getDeclarationLineList().sort(Comparator.comparing(DeclarationLine::getSequence));
    }
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public Declaration addPack(Declaration declaration, Pack pack, BigDecimal packQty)
      throws AxelorException {

    List<PackLine> packLineList = pack.getComponents();
    if (ObjectUtils.isEmpty(packLineList)) {
      return declaration;
    }
    packLineList.sort(Comparator.comparing(PackLine::getSequence));
    Integer sequence = -1;

    List<DeclarationLine> soLines = declaration.getDeclarationLineList();
    if (soLines != null && !soLines.isEmpty()) {
      sequence = soLines.stream().mapToInt(DeclarationLine::getSequence).max().getAsInt();
    }

    BigDecimal conversionRate = BigDecimal.valueOf(1.00);
    if (pack.getCurrency() != null
        && !pack.getCurrency().getCodeISO().equals(declaration.getCurrency().getCodeISO())) {
      try {
        conversionRate =
            Beans.get(CurrencyConversionFactory.class)
                .getCurrencyConversionService()
                .convert(pack.getCurrency(), declaration.getCurrency());
      } catch (MalformedURLException | JSONException | AxelorException e) {
        TraceBackService.trace(e);
      }
    }

    if (Boolean.FALSE.equals(pack.getDoNotDisplayHeaderAndEndPack())) {
      if (declarationLineService.getPackLineTypes(packLineList) == null
          || !declarationLineService
              .getPackLineTypes(packLineList)
              .contains(PackLineRepository.TYPE_START_OF_PACK)) {
        sequence++;
      }
      soLines =
          declarationLineService.createNonStandardSOLineFromPack(
              pack, declaration, packQty, soLines, sequence);
    }

    boolean doNotDisplayHeaderAndEndPack =
        Boolean.TRUE.equals(pack.getDoNotDisplayHeaderAndEndPack());
    DeclarationLine soLine;
    for (PackLine packLine : packLineList) {
      if (doNotDisplayHeaderAndEndPack
          && (Objects.equals(packLine.getTypeSelect(), PackLineRepository.TYPE_START_OF_PACK)
              || Objects.equals(packLine.getTypeSelect(), PackLineRepository.TYPE_END_OF_PACK))) {
        continue;
      }
      soLine =
          declarationLineService.createDeclarationLine(
              packLine, declaration, packQty, conversionRate, ++sequence);
      if (soLine != null) {
        soLine.setDeclaration(declaration);
        soLines.add(soLine);
      }
    }

    if (soLines != null && !soLines.isEmpty()) {
      try {
        declaration = declarationComputeService.computeDeclaration(declaration);
        declarationMarginService.computeMarginDeclaration(declaration);
      } catch (AxelorException e) {
        TraceBackService.trace(e);
      }

      declarationRepo.save(declaration);
    }
    return declaration;
  }

  @Override
  public List<DeclarationLine> handleComplementaryProducts(Declaration declaration)
      throws AxelorException {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (declarationLineList == null) {
      declarationLineList = new ArrayList<>();
    }

    DeclarationLine originSoLine = null;
    for (DeclarationLine soLine : declarationLineList) {
      if (soLine.getIsComplementaryProductsUnhandledYet()) {
        originSoLine = soLine;
        if (originSoLine.getManualId() == null || originSoLine.getManualId().equals("")) {
          this.setNewManualId(originSoLine);
        }
        break;
      }
    }

    if (originSoLine != null
        && originSoLine.getProduct() != null
        && originSoLine.getSelectedComplementaryProductList() != null) {
      for (ComplementaryProductSelected compProductSelected :
          originSoLine.getSelectedComplementaryProductList()) {
        // Search if there is already a line for this product to modify or remove
        DeclarationLine newSoLine = null;
        for (DeclarationLine soLine : declarationLineList) {
          if (originSoLine.getManualId().equals(soLine.getParentId())
              && soLine.getProduct() != null
              && soLine.getProduct().equals(compProductSelected.getProduct())) {
            // Edit line if it already exists instead of recreating, otherwise remove if already
            // exists and is no longer selected
            if (compProductSelected.getIsSelected()) {
              newSoLine = soLine;
            } else {
              declarationLineList.remove(soLine);
            }
            break;
          }
        }

        if (newSoLine == null) {
          if (compProductSelected.getIsSelected()) {
            newSoLine = new DeclarationLine();
            newSoLine.setProduct(compProductSelected.getProduct());
            newSoLine.setDeclaration(declaration);
            newSoLine.setQty(compProductSelected.getQty());

            declarationLineService.computeProductInformation(newSoLine, newSoLine.getDeclaration());
            declarationLineService.computeValues(newSoLine.getDeclaration(), newSoLine);

            newSoLine.setParentId(originSoLine.getManualId());

            int targetIndex = declarationLineList.indexOf(originSoLine) + 1;
            declarationLineList.add(targetIndex, newSoLine);
          }
        } else {
          newSoLine.setQty(compProductSelected.getQty());

          declarationLineService.computeProductInformation(newSoLine, newSoLine.getDeclaration());
          declarationLineService.computeValues(newSoLine.getDeclaration(), newSoLine);
        }
      }
      originSoLine.setIsComplementaryProductsUnhandledYet(false);
    }

    for (int i = 0; i < declarationLineList.size(); i++) {
      declarationLineList.get(i).setSequence(i);
    }

    return declarationLineList;
  }

  @Override
  public void checkUnauthorizedDiscounts(Declaration declaration) throws AxelorException {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (declarationLineList != null) {
      for (DeclarationLine declarationLine : declarationLineList) {
        BigDecimal maxDiscountAuthorized =
            declarationLineService.computeMaxDiscount(declaration, declarationLine);
        if (declarationLine.getDiscountDerogation() != null && maxDiscountAuthorized != null) {
          maxDiscountAuthorized = declarationLine.getDiscountDerogation().max(maxDiscountAuthorized);
        }
        if (maxDiscountAuthorized != null
            && declarationLineService.isDeclarationLineDiscountGreaterThanMaxDiscount(
                declarationLine, maxDiscountAuthorized)) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(SaleExceptionMessage.SALE_ORDER_DISCOUNT_TOO_HIGH));
        }
      }
    }
  }

  @Transactional
  protected void setNewManualId(DeclarationLine declarationLine) {
    declarationLine.setManualId(JpaSequence.nextValue("sale.order.line.idSeq"));
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Declaration updateProductQtyWithPackHeaderQty(Declaration declaration) throws AxelorException {
    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    boolean isStartOfPack = false;
    BigDecimal newQty = BigDecimal.ZERO;
    BigDecimal oldQty = BigDecimal.ZERO;
    this.sortDeclarationLineList(declaration);

    for (DeclarationLine SOLine : declarationLineList) {

      if (SOLine.getTypeSelect() == DeclarationLineRepository.TYPE_START_OF_PACK && !isStartOfPack) {
        newQty = SOLine.getQty();
        oldQty = declarationLineRepo.find(SOLine.getId()).getQty();
        if (newQty.compareTo(oldQty) != 0) {
          isStartOfPack = true;
          SOLine = EntityHelper.getEntity(SOLine);
          declarationLineRepo.save(SOLine);
        }
      } else if (isStartOfPack) {
        if (SOLine.getTypeSelect() == DeclarationLineRepository.TYPE_END_OF_PACK) {
          break;
        }
        declarationLineService.updateProductQty(SOLine, declaration, oldQty, newQty);
      }
    }
    return declaration;
  }

  @Transactional(rollbackOn = Exception.class)
  public Declaration separateInNewQuotation(
      Declaration declaration, ArrayList<LinkedHashMap<String, Object>> declarationLines)
      throws AxelorException {

    declaration = declarationRepo.find(declaration.getId());
    List<DeclarationLine> originalSOLines = declaration.getDeclarationLineList();

    Declaration copyDeclaration = declarationRepo.copy(declaration, true);
    copyDeclaration.clearDeclarationLineList();
    declarationRepo.save(copyDeclaration);

    for (LinkedHashMap<String, Object> soLine : declarationLines) {
      if (!soLine.containsKey("selected") || !(boolean) soLine.get("selected")) {
        continue;
      }

      DeclarationLine declarationLine =
          declarationLineRepo.find(Long.parseLong(soLine.get("id").toString()));
      List<DeclarationLine> separatedSOLines = new ArrayList<>();
      separatedSOLines.add(declarationLine);
      separatedSOLines.addAll(
          originalSOLines.stream()
              .filter(
                  soline ->
                      StringUtils.notBlank(declarationLine.getManualId())
                          && declarationLine.getManualId().equals(soline.getParentId()))
              .collect(Collectors.toList()));
      manageSeparatedSOLines(separatedSOLines, originalSOLines, copyDeclaration);
    }

    copyDeclaration = declarationComputeService.computeDeclaration(copyDeclaration);
    declarationRepo.save(copyDeclaration);

    // refresh the origin sale order to refresh the field declarationLineList
    JPA.refresh(declaration);

    declaration = declarationComputeService.computeDeclaration(declaration);
    declarationRepo.save(declaration);

    return copyDeclaration;
  }

  protected void manageSeparatedSOLines(
      List<DeclarationLine> separatedSOLines,
      List<DeclarationLine> originalSOLines,
      Declaration copyDeclaration) {

    for (DeclarationLine separatedLine : separatedSOLines) {
      copyDeclaration.addDeclarationLineListItem(separatedLine);
      originalSOLines.stream()
          .filter(soLine -> separatedLine.equals(soLine.getMainDeclarationLine()))
          .forEach(copyDeclaration::addDeclarationLineListItem);
    }
  }

  @Override
  public void manageComplementaryProductSOLines(Declaration declaration) throws AxelorException {

    List<DeclarationLine> declarationLineList = declaration.getDeclarationLineList();
    if (declaration.getClientPartner() == null) {
      return;
    }
    List<ComplementaryProduct> complementaryProducts =
        declaration.getClientPartner().getComplementaryProductList();

    if (CollectionUtils.isEmpty(declarationLineList)
        || CollectionUtils.isEmpty(complementaryProducts)) {
      return;
    }

    List<DeclarationLine> newComplementarySOLines = new ArrayList<>();
    for (ComplementaryProduct complementaryProduct : complementaryProducts) {
      Product product = complementaryProduct.getProduct();
      if (product == null) {
        continue;
      }

      if (complementaryProduct.getGenerationTypeSelect()
          == ComplementaryProductRepository.GENERATION_TYPE_SALE_ORDER) {
        DeclarationLine declarationLine =
            Collections.max(declarationLineList, Comparator.comparing(DeclarationLine::getSequence));
        if (declarationLineList.stream()
            .anyMatch(
                line ->
                    product.equals(line.getProduct())
                        && line.getIsComplementaryPartnerProductsHandled())) {
          continue;
        }
        newComplementarySOLines.addAll(
            declarationLineService.manageComplementaryProductDeclarationLine(
                complementaryProduct, declaration, declarationLine));
      } else {
        for (DeclarationLine declarationLine : declarationLineList) {
          newComplementarySOLines.addAll(
              declarationLineService.manageComplementaryProductDeclarationLine(
                  complementaryProduct, declaration, declarationLine));
        }
      }
    }
    newComplementarySOLines.forEach(declaration::addDeclarationLineListItem);
    declarationComputeService.computeDeclaration(declaration);
  }
}
