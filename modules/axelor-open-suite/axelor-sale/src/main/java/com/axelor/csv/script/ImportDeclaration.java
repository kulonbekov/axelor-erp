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
package com.axelor.csv.script;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.repo.DeclarationManagementRepository;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import com.axelor.apps.sale.service.declaration.DeclarationComputeService;
import com.axelor.apps.sale.service.declaration.DeclarationService;
import com.axelor.apps.sale.service.declaration.DeclarationWorkflowService;
import com.google.inject.Inject;
import java.util.Map;

public class ImportDeclaration {

  @Inject DeclarationManagementRepository declarationRepo;

  protected DeclarationService declarationService;
  protected DeclarationComputeService declarationComputeService;
  protected DeclarationWorkflowService declarationWorkflowService;
  protected SequenceService sequenceService;

  @Inject
  public ImportDeclaration(
      DeclarationService declarationService,
      DeclarationComputeService declarationComputeService,
      DeclarationWorkflowService declarationWorkflowService,
      SequenceService sequenceService) {
    this.declarationService = declarationService;
    this.declarationComputeService = declarationComputeService;
    this.declarationWorkflowService = declarationWorkflowService;
    this.sequenceService = sequenceService;
  }

  public Object importDeclaration(Object bean, Map<String, Object> values) throws AxelorException {
    assert bean instanceof Declaration;

    Declaration declaration = (Declaration) bean;

    declarationService.computeAddressStr(declaration);

    declaration = declarationComputeService.computeDeclaration(declaration);

    if (declaration.getStatusSelect() == 1) {
      declaration.setDeclarationSeq(sequenceService.getDraftSequenceNumber(declaration));
      declarationRepo.computeFullName(declaration);
    } else {
      // Setting the status to draft or else we can't finalize it.
      declaration.setStatusSelect(DeclarationRepository.STATUS_DRAFT_QUOTATION);
      declarationWorkflowService.finalizeQuotation(declaration);
    }

    return declaration;
  }
}
