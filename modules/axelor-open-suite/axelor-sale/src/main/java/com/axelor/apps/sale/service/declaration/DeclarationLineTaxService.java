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
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.DeclarationLineTax;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclarationLineTaxService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private DeclarationService declarationService;
  @Inject private DeclarationToolService declarationToolService;

  /**
   * Créer les lignes de TVA du devis. La création des lignes de TVA se basent sur les lignes de
   * devis ainsi que les sous-lignes de devis de celles-ci. Si une ligne de devis comporte des
   * sous-lignes de devis, alors on se base uniquement sur celles-ci.
   *
   * @param declaration Le devis de vente.
   * @param declarationLineList Les lignes du devis de vente.
   * @return La liste des lignes de taxe du devis de vente.
   */
  public List<DeclarationLineTax> createsDeclarationLineTax(
      Declaration declaration, List<DeclarationLine> declarationLineList) {

    List<DeclarationLineTax> declarationLineTaxList = new ArrayList<DeclarationLineTax>();
    Map<TaxLine, DeclarationLineTax> map = new HashMap<TaxLine, DeclarationLineTax>();
    Set<String> specificNotes = new HashSet<String>();

    boolean customerSpecificNote = false;
    FiscalPosition fiscalPosition = declaration.getFiscalPosition();
    if (fiscalPosition != null) {
      customerSpecificNote = fiscalPosition.getCustomerSpecificNote();
    }

    if (declarationLineList != null && !declarationLineList.isEmpty()) {

      LOG.debug("Creation of VAT lines for sale order lines.");

      for (DeclarationLine declarationLine : declarationLineList) {

        TaxLine taxLine = declarationLine.getTaxLine();

        if (taxLine != null) {

          LOG.debug("Tax {}", taxLine);

          if (map.containsKey(taxLine)) {

            DeclarationLineTax declarationLineTax = map.get(taxLine);

            declarationLineTax.setExTaxBase(
                declarationLineTax.getExTaxBase().add(declarationLine.getExTaxTotal()));

          } else {

            DeclarationLineTax declarationLineTax = new DeclarationLineTax();
            declarationLineTax.setDeclaration(declaration);

            declarationLineTax.setExTaxBase(declarationLine.getExTaxTotal());

            declarationLineTax.setTaxLine(taxLine);
            map.put(taxLine, declarationLineTax);
          }
        }

        if (!customerSpecificNote) {
          TaxEquiv taxEquiv = declarationLine.getTaxEquiv();
          if (taxEquiv != null && taxEquiv.getSpecificNote() != null) {
            specificNotes.add(taxEquiv.getSpecificNote());
          }
        }
      }
    }

    for (DeclarationLineTax declarationLineTax : map.values()) {

      // Dans la devise de la facture
      BigDecimal exTaxBase = declarationLineTax.getExTaxBase();
      BigDecimal taxTotal = BigDecimal.ZERO;
      if (declarationLineTax.getTaxLine() != null) {
        taxTotal =
            declarationToolService.computeAmount(
                exTaxBase, declarationLineTax.getTaxLine().getValue().divide(new BigDecimal(100)));
        declarationLineTax.setTaxTotal(taxTotal);
      }
      declarationLineTax.setInTaxTotal(exTaxBase.add(taxTotal));
      declarationLineTaxList.add(declarationLineTax);

      LOG.debug(
          "VAT line : VAT total => {}, W.T. total => {}",
          new Object[] {declarationLineTax.getTaxTotal(), declarationLineTax.getInTaxTotal()});
    }

    if (!customerSpecificNote) {
      declaration.setSpecificNotes(Joiner.on('\n').join(specificNotes));
    } else {
      declaration.setSpecificNotes(declaration.getClientPartner().getSpecificTaxNote());
    }

    return declarationLineTaxList;
  }
}
