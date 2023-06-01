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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.sale.db.Declaration;
import com.axelor.apps.sale.db.DeclarationLine;
import com.axelor.apps.sale.db.repo.DeclarationRepository;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDeclarationStockService {

  private DeclarationStockServiceImpl declarationStockService;

  @Before
  public void prepare() throws AxelorException {
    declarationStockService = mock(DeclarationStockServiceImpl.class);
    when(declarationStockService.isStockMoveProduct(any(DeclarationLine.class), any(Declaration.class)))
        .thenReturn(true);
    doCallRealMethod().when(declarationStockService).computeDeliveryState(any(Declaration.class));
  }

  @Test
  public void testUpdateDeliveryStateDeclarationWithNull() throws AxelorException {
    Declaration declaration = new Declaration();
    Assert.assertEquals(
        DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED,
        declarationStockService.computeDeliveryState(declaration));
  }

  @Test
  public void testUpdateDeliveryStateEmptyDeclaration() throws AxelorException {
    Declaration declaration = new Declaration();
    declaration.setDeclarationLineList(new ArrayList<>());
    Assert.assertEquals(
        DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED,
        declarationStockService.computeDeliveryState(declaration));
  }

  @Test
  public void testUpdateDeliveryStateDeliveredDeclaration() throws AxelorException {
    Declaration declaration = new Declaration();
    declaration.setDeclarationLineList(new ArrayList<>());
    DeclarationLine declarationLine1 = new DeclarationLine();
    DeclarationLine declarationLine2 = new DeclarationLine();
    declarationLine1.setDeliveryState(DeclarationRepository.DELIVERY_STATE_DELIVERED);
    declarationLine2.setDeliveryState(DeclarationRepository.DELIVERY_STATE_DELIVERED);
    declaration.addDeclarationLineListItem(declarationLine1);
    declaration.addDeclarationLineListItem(declarationLine2);

    Assert.assertEquals(
        DeclarationRepository.DELIVERY_STATE_DELIVERED,
        declarationStockService.computeDeliveryState(declaration));
  }

  @Test
  public void testUpdateDeliveryStatePartiallyDeliveredDeclaration() throws AxelorException {
    Declaration declaration = new Declaration();
    DeclarationLine declarationLine1 = new DeclarationLine();
    DeclarationLine declarationLine2 = new DeclarationLine();
    declarationLine1.setDeliveryState(DeclarationRepository.DELIVERY_STATE_DELIVERED);
    declarationLine2.setDeliveryState(DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED);
    declaration.addDeclarationLineListItem(declarationLine1);
    declaration.addDeclarationLineListItem(declarationLine2);

    declarationStockService.updateDeliveryState(declaration);
    Assert.assertEquals(
        DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED,
        declarationStockService.computeDeliveryState(declaration));
  }

  @Test
  public void testUpdateDeliveryStatePartiallyDelivered2Declaration() throws AxelorException {
    Declaration declaration = new Declaration();
    DeclarationLine declarationLine1 = new DeclarationLine();
    DeclarationLine declarationLine2 = new DeclarationLine();
    declarationLine1.setDeliveryState(DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED);
    declarationLine2.setDeliveryState(DeclarationRepository.DELIVERY_STATE_DELIVERED);
    declaration.addDeclarationLineListItem(declarationLine1);
    declaration.addDeclarationLineListItem(declarationLine2);

    Assert.assertEquals(
        DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED,
        declarationStockService.computeDeliveryState(declaration));
  }

  @Test
  public void testUpdateDeliveryStatePartiallyDeliveredLinesDeclaration() throws AxelorException {
    Declaration declaration = new Declaration();
    declaration.setDeclarationLineList(new ArrayList<>());
    DeclarationLine declarationLine1 = new DeclarationLine();
    DeclarationLine declarationLine2 = new DeclarationLine();
    DeclarationLine declarationLine3 = new DeclarationLine();
    declarationLine1.setDeliveryState(DeclarationRepository.DELIVERY_STATE_DELIVERED);
    declarationLine2.setDeliveryState(DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED);
    declarationLine3.setDeliveryState(DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED);
    declaration.addDeclarationLineListItem(declarationLine1);
    declaration.addDeclarationLineListItem(declarationLine2);
    declaration.addDeclarationLineListItem(declarationLine3);

    Assert.assertEquals(
        DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED,
        declarationStockService.computeDeliveryState(declaration));
  }

  @Test
  public void testUpdateDeliveryStateOnlyPartiallyDeliveredLinesDeclaration() throws AxelorException {
    Declaration declaration = new Declaration();
    declaration.setDeclarationLineList(new ArrayList<>());
    DeclarationLine declarationLine1 = new DeclarationLine();
    DeclarationLine declarationLine2 = new DeclarationLine();
    DeclarationLine declarationLine3 = new DeclarationLine();
    declarationLine1.setDeliveryState(DeclarationRepository.DELIVERY_STATE_NOT_DELIVERED);
    declarationLine2.setDeliveryState(DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED);
    declarationLine3.setDeliveryState(DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED);
    declaration.addDeclarationLineListItem(declarationLine1);
    declaration.addDeclarationLineListItem(declarationLine2);
    declaration.addDeclarationLineListItem(declarationLine3);

    Assert.assertEquals(
        DeclarationRepository.DELIVERY_STATE_PARTIALLY_DELIVERED,
        declarationStockService.computeDeliveryState(declaration));
  }
}
