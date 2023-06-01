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
package com.axelor.apps.sale.service.declaration.print;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.Declaration;
import java.io.File;
import java.io.IOException;
import java.util.List;

public interface DeclarationPrintService {

  /**
   * Print a list of sale orders in the same output.
   *
   * @param ids ids of the sale order.
   * @return the link to the generated file.
   * @throws IOException
   */
  String printDeclarations(List<Long> ids) throws IOException;

  ReportSettings prepareReportSettings(Declaration declaration, boolean proforma, String format)
      throws AxelorException;

  File print(Declaration declaration, boolean proforma, String format) throws AxelorException;

  String printDeclaration(Declaration declaration, boolean proforma, String format)
      throws AxelorException, IOException;
}
