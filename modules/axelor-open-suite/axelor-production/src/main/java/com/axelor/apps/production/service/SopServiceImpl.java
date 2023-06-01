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
package com.axelor.apps.production.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Period;
import com.axelor.apps.base.db.ProductCategory;
import com.axelor.apps.base.db.Year;
import com.axelor.apps.base.db.repo.CurrencyRepository;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.Sop;
import com.axelor.apps.production.db.SopLine;
import com.axelor.apps.production.db.repo.SopLineRepository;
import com.axelor.apps.production.db.repo.SopRepository;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationLineRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SopServiceImpl implements SopService {

  public static final int FETCH_LIMIT = 1;
  protected AppBaseService appBaseService;
  protected SopRepository sopRepo;
  protected PeriodRepository periodRepo;
  protected DeclarationLineRepository declarationLineRepo;
  protected SopLineRepository sopLineRepo;
  protected CurrencyService currencyService;
  protected CurrencyRepository currencyRepo;
  protected LocalDate today;

  @Inject
  SopServiceImpl(
      SopRepository sopRepo,
      PeriodRepository periodRepo,
      DeclarationLineRepository declarationLineRepo,
      SopLineRepository sopLineRepo,
      CurrencyService currencyService,
      AppBaseService appBaseService,
      CurrencyRepository currencyRepo) {
    this.sopRepo = sopRepo;
    this.periodRepo = periodRepo;
    this.declarationLineRepo = declarationLineRepo;
    this.sopLineRepo = sopLineRepo;
    this.currencyService = currencyService;
    this.appBaseService = appBaseService;
    this.currencyRepo = currencyRepo;
  }

  @Override
  public void generateSOPLines(Sop sop) throws AxelorException {
    today = appBaseService.getTodayDate(sop.getCompany());
    List<Period> yearPeriods =
        periodRepo
            .all()
            .filter("self.year = :year AND self.statusSelect = :status")
            .bind("year", sop.getYear())
            .bind("status", PeriodRepository.STATUS_OPENED)
            .fetch();
    List<SopLine> sopLineList = new ArrayList<SopLine>();
    for (Period period : yearPeriods) {
      sopLineList.add(this.createSOPLine(sop, period));
    }
    this.linkSOPLines(sop, sopLineList);
    this.updateSOPLines(sop);
  }

  @Transactional
  protected void linkSOPLines(Sop sop, List<SopLine> sopLineList) {
    sop = sopRepo.find(sop.getId());
    sop.clearSopLineList();
    for (SopLine sopLine : sopLineList) {
      sop.addSopLineListItem(sopLine);
    }
    sop.setIsGenerated(true);
    sopRepo.save(sop);
  }

  protected SopLine createSOPLine(Sop sop, Period period) {
    SopLine sopLine = new SopLine();
    sopLine.setPeriod(period);
    sopLine.setYear(period.getYear());
    sopLine.setCurrency(sop.getCompany().getCurrency());
    sop.addSopLineListItem(sopLine);
    return sopLine;
  }

  protected void updateSOPLines(Sop sop) throws AxelorException {
    for (SopLine sopLine : sop.getSopLineList()) {
      sop = sopRepo.find(sop.getId());
      if (sop.getIsForecastOnHistoric()) {
        this.setSalesForecast(
            sopLine, sop.getProductCategory(), sop.getStockLocationSet(), sop.getCompany());
      }
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void setSalesForecast(
      SopLine sopLine,
      ProductCategory category,
      Set<StockLocation> stockLocationSet,
      Company company)
      throws AxelorException {

    sopLine = sopLineRepo.find(sopLine.getId());
    LocalDate fromDate = sopLine.getPeriod().getFromDate();
    LocalDate toDate = sopLine.getPeriod().getToDate();
    Year year = sopLine.getSop().getYearbasedHistoric();
    if (year != null) {
      fromDate = fromDate.withYear(year.getFromDate().getYear());
      toDate = toDate.withYear(year.getToDate().getYear());
    }
    Currency actualCurrency = company.getCurrency();
    ArrayList<Integer> statusList = new ArrayList<Integer>();
    statusList.add(DeclarationRepository.STATUS_ORDER_COMPLETED);
    statusList.add(DeclarationRepository.STATUS_ORDER_CONFIRMED);

    List<Long> stockLocationIds =
        stockLocationSet.stream()
            .map(stockLocation -> stockLocation.getId())
            .collect(Collectors.toList());

    BigDecimal exTaxSum = BigDecimal.ZERO;
    Query<DeclarationLine> query =
        declarationLineRepo
            .all()
            .filter(
                "self.declaration.company = ?1 "
                    + "AND self.declaration.statusSelect in (?2) "
                    + "AND self.product.productCategory = ?3 "
                    + "AND self.declaration.stockLocation.id in (?4)",
                company,
                statusList,
                category,
                stockLocationIds)
            .order("id");
    int offset = 0;
    List<DeclarationLine> declarationLineList;
    while (!(declarationLineList = query.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      offset += FETCH_LIMIT;
      actualCurrency = currencyRepo.find(actualCurrency.getId());
      for (DeclarationLine declarationLine : declarationLineList) {
        LocalDate usedDate =
            declarationLine.getDesiredDeliveryDate() != null
                ? declarationLine.getDesiredDeliveryDate()
                : declarationLine.getEstimatedShippingDate() != null
                    ? declarationLine.getEstimatedShippingDate()
                    : declarationLine.getDeclaration().getEstimatedShippingDate() != null
                        ? declarationLine.getDeclaration().getEstimatedShippingDate()
                        : declarationLine.getDeclaration().getConfirmationDateTime().toLocalDate();

        if (usedDate.isAfter(fromDate) && usedDate.isBefore(toDate)) {
          if (declarationLine.getDeclaration().getCurrency().equals(actualCurrency)) {
            exTaxSum =
                exTaxSum
                    .add(declarationLine.getExTaxTotal().multiply(sopLine.getSop().getGrowthCoef()))
                    .setScale(2, RoundingMode.HALF_UP);
          } else {
            exTaxSum =
                exTaxSum.add(
                    currencyService
                        .getAmountCurrencyConvertedAtDate(
                            declarationLine.getDeclaration().getCurrency(),
                            actualCurrency,
                            declarationLine.getExTaxTotal(),
                            today)
                        .multiply(sopLine.getSop().getGrowthCoef())
                        .setScale(2, RoundingMode.HALF_UP));
          }
        }
      }
      JPA.clear();
    }
    sopLine = sopLineRepo.find(sopLine.getId());
    sopLine.setSopSalesForecast(exTaxSum);
    sopLineRepo.save(sopLine);
  }
}
