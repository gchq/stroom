/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Action } from "redux";
import * as uuidv4 from "uuid/v4";

import {
  prepareReducerById,
  ActionId,
  StateById
} from "../../lib/redux-actions-ts";

import {
  assignRandomUuids,
  updateItemInTree,
  addItemsToTree,
  deleteItemFromTree,
  moveItemsInTree
} from "../../lib/treeUtils";

import { toString } from "./expressionBuilderUtils";
import {
  ExpressionOperatorType,
  ExpressionOperatorWithUuid,
  ExpressionTermWithUuid,
  ExpressionHasUuid
} from "../../types";

const EXPRESSION_EDITOR_CREATED = "EXPRESSION_EDITOR_CREATED";
const EXPRESSION_EDITOR_DESTROYED = "EXPRESSION_EDITOR_DESTROYED";
const EXPRESSION_CHANGED = "EXPRESSION_CHANGED";
const EXPRESSION_TERM_ADDED = "EXPRESSION_TERM_ADDED";
const EXPRESSION_OPERATOR_ADDED = "EXPRESSION_OPERATOR_ADDED";
const EXPRESSION_ITEM_UPDATED = "EXPRESSION_ITEM_UPDATED";
const EXPRESSION_ITEM_DELETED = "EXPRESSION_ITEM_DELETED";
const EXPRESSION_ITEM_MOVED = "EXPRESSION_ITEM_MOVED";

export interface ExpressionEditorCreatedAction
  extends Action<"EXPRESSION_EDITOR_CREATED">,
    ActionId {}
export interface ExpressionEditorDestroyedAction
  extends Action<"EXPRESSION_EDITOR_DESTROYED">,
    ActionId {}
export interface ExpressionChangedAction
  extends Action<"EXPRESSION_CHANGED">,
    ActionId {
  expression: ExpressionOperatorWithUuid | ExpressionOperatorType;
}
export interface ExpressionTermAddedAction
  extends Action<"EXPRESSION_TERM_ADDED">,
    ActionId {
  itemId: string;
}
export interface ExpressionOperatorAddedAction
  extends Action<"EXPRESSION_OPERATOR_ADDED">,
    ActionId {
  itemId: string;
}
export interface ExpressionItemUpdatedAction
  extends Action<"EXPRESSION_ITEM_UPDATED">,
    ActionId {
  itemId: string;
  updates: object;
}
export interface ExpressionItemDeletedAction
  extends Action<"EXPRESSION_ITEM_DELETED">,
    ActionId {
  itemId: string;
}
export interface ExpressionItemMovedAction
  extends Action<"EXPRESSION_ITEM_MOVED">,
    ActionId {
  itemToMove: ExpressionHasUuid;
  destination: ExpressionHasUuid;
}

export const actionCreators = {
  expressionEditorCreated: (id: string): ExpressionEditorCreatedAction => ({
    type: EXPRESSION_EDITOR_CREATED,
    id
  }),
  expressionEditorDestroyed: (id: string): ExpressionEditorDestroyedAction => ({
    type: EXPRESSION_EDITOR_DESTROYED,
    id
  }),
  expressionChanged: (
    id: string,
    expression: ExpressionOperatorType
  ): ExpressionChangedAction => ({
    type: EXPRESSION_CHANGED,
    id,
    expression
  }),
  expressionTermAdded: (
    id: string,
    itemId: string
  ): ExpressionTermAddedAction => ({
    type: EXPRESSION_TERM_ADDED,
    id,
    itemId
  }),
  expressionOperatorAdded: (
    id: string,
    itemId: string
  ): ExpressionOperatorAddedAction => ({
    type: EXPRESSION_OPERATOR_ADDED,
    id,
    itemId
  }),
  expressionItemUpdated: (
    id: string,
    itemId: string,
    updates: object
  ): ExpressionItemUpdatedAction => ({
    type: EXPRESSION_ITEM_UPDATED,
    id,
    itemId,
    updates
  }),
  expressionItemDeleted: (
    id: string,
    itemId: string
  ): ExpressionItemDeletedAction => ({
    type: EXPRESSION_ITEM_DELETED,
    id,
    itemId
  }),
  expressionItemMoved: (
    id: string,
    itemToMove: ExpressionHasUuid,
    destination: ExpressionHasUuid
  ): ExpressionItemMovedAction => ({
    type: EXPRESSION_ITEM_MOVED,
    id,
    itemToMove,
    destination
  })
};

const getNewTerm = (): ExpressionTermWithUuid => ({
  uuid: uuidv4(),
  type: "term",
  condition: "EQUALS",
  enabled: true
});

const getNewOperator = (): ExpressionOperatorWithUuid => ({
  uuid: uuidv4(),
  type: "operator",
  op: "AND",
  enabled: true,
  children: []
});

export interface StoreStateById {
  expression: ExpressionOperatorWithUuid;
  expressionAsString?: string;
}

export interface StoreState extends StateById<StoreStateById> {}

export const defaultStatePerId: StoreStateById = {
  expression: getNewOperator()
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<ExpressionChangedAction>(
    EXPRESSION_CHANGED,
    (state, { expression }) => ({
      ...state,
      expression: assignRandomUuids(expression) as ExpressionOperatorWithUuid,
      expressionAsString: toString(expression)
    })
  )
  .handleAction<ExpressionTermAddedAction>(
    EXPRESSION_TERM_ADDED,
    (state = defaultStatePerId, { itemId }) => ({
      expression: addItemsToTree(state.expression, itemId, [
        getNewTerm()
      ]) as ExpressionOperatorWithUuid,
      expressionAsString: toString(state.expression)
    })
  )
  .handleAction<ExpressionOperatorAddedAction>(
    EXPRESSION_OPERATOR_ADDED,
    (state = defaultStatePerId, { itemId }) => ({
      expression: addItemsToTree(state.expression, itemId, [
        getNewOperator()
      ]) as ExpressionOperatorWithUuid,
      expressionAsString: toString(state.expression)
    })
  )
  .handleAction<ExpressionItemUpdatedAction>(
    EXPRESSION_ITEM_UPDATED,
    (state = defaultStatePerId, { itemId, updates }) => ({
      expression: updateItemInTree(
        state.expression,
        itemId,
        updates
      ) as ExpressionOperatorWithUuid,
      expressionAsString: toString(state.expression)
    })
  )
  .handleAction<ExpressionItemDeletedAction>(
    EXPRESSION_ITEM_DELETED,
    (state = defaultStatePerId, { itemId }) => ({
      ...state,
      expression: deleteItemFromTree(
        state.expression,
        itemId
      ) as ExpressionOperatorWithUuid,
      expressionAsString: toString(state.expression)
    })
  )
  .handleAction<ExpressionItemMovedAction>(
    EXPRESSION_ITEM_MOVED,
    (state = defaultStatePerId, { destination, itemToMove }) => ({
      expression: moveItemsInTree(state.expression, destination, [
        itemToMove
      ]) as ExpressionOperatorWithUuid,
      expressionAsString: toString(state.expression)
    })
  )
  .getReducer();
