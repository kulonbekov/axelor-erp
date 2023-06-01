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
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.stock.db.StockLocation;
import java.math.BigDecimal;

public interface DeclarationSupplychainService {

  public void updateToConfirmedStatus(Declaration declaration) throws AxelorException;

  public String createShipmentCostLine(Declaration declaration) throws AxelorException;

  boolean alreadyHasShippingCostLine(Declaration declaration, Product shippingCostProduct);

  DeclarationLine createShippingCostLine(Declaration declaration, Product shippingCostProduct)
      throws AxelorException;

  String removeShipmentCostLine(Declaration declaration);

  BigDecimal computeExTaxTotalWithoutShippingLines(Declaration declaration);

  public void setDefaultInvoicedAndDeliveredPartnersAndAddresses(Declaration declaration);

  StockLocation getStockLocation(Partner clientPartner, Company company) throws AxelorException;

  StockLocation getToStockLocation(Partner clientPartner, Company company) throws AxelorException;

  public void updateAmountToBeSpreadOverTheTimetable(Declaration declaration);
}
