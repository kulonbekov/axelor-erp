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

import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.supplychain.db.MrpLineType;

public interface MrpDeclarationCheckLateSaleService {

  /**
   * Determine if the declarationLine should generate a DeclarationMrpLine with regard to the late sales
   * parameter of the mrpLineType
   *
   * @param declarationLine
   * @param mrpLineType
   * @return a boolean indicating if a line should be created
   */
  boolean checkLateSalesParameter(DeclarationLine declarationLine, MrpLineType mrpLineType);
}