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

export const EXPRESSION_EDITOR_CREATED = "EXPRESSION_EDITOR_CREATED";
export const EXPRESSION_EDITOR_DESTROYED = "EXPRESSION_EDITOR_DESTROYED";
export const EXPRESSION_CHANGED = "EXPRESSION_CHANGED";
export const EXPRESSION_TERM_ADDED = "EXPRESSION_TERM_ADDED";
export const EXPRESSION_OPERATOR_ADDED = "EXPRESSION_OPERATOR_ADDED";
export const EXPRESSION_ITEM_UPDATED = "EXPRESSION_ITEM_UPDATED";
export const EXPRESSION_ITEM_DELETE_REQUESTED =
  "EXPRESSION_ITEM_DELETE_REQUESTED";
export const EXPRESSION_ITEM_DELETE_CANCELLED =
  "EXPRESSION_ITEM_DELETE_CANCELLED";
export const EXPRESSION_ITEM_DELETE_CONFIRMED =
  "EXPRESSION_ITEM_DELETE_CONFIRMED";
export const EXPRESSION_ITEM_MOVED = "EXPRESSION_ITEM_MOVED";

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
  operatorId: string;
}
export interface ExpressionOperatorAddedAction
  extends Action<"EXPRESSION_OPERATOR_ADDED">,
    ActionId {
  operatorId: string;
}
export interface ExpressionItemUpdatedAction
  extends Action<"EXPRESSION_ITEM_UPDATED">,
    ActionId {
  itemId: string;
  updates: object;
}
export interface ExpressionItemDeleteRequestedAction
  extends Action<"EXPRESSION_ITEM_DELETE_REQUESTED">,
    ActionId {
  pendingDeletionOperatorId: string;
}
export interface ExpressionItemDeleteCancelledAction
  extends Action<"EXPRESSION_ITEM_DELETE_CANCELLED">,
    ActionId {}
export interface ExpressionItemDeleteConfirmedAction
  extends Action<"EXPRESSION_ITEM_DELETE_CONFIRMED">,
    ActionId {}
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
    operatorId: string
  ): ExpressionTermAddedAction => ({
    type: EXPRESSION_TERM_ADDED,
    id,
    operatorId
  }),
  expressionOperatorAdded: (
    id: string,
    operatorId: string
  ): ExpressionOperatorAddedAction => ({
    type: EXPRESSION_OPERATOR_ADDED,
    id,
    operatorId
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
  expressionItemDeleteRequested: (
    id: string,
    pendingDeletionOperatorId: string
  ): ExpressionItemDeleteRequestedAction => ({
    type: EXPRESSION_ITEM_DELETE_REQUESTED,
    id,
    pendingDeletionOperatorId
  }),
  expressionItemDeleteCancelled: (
    id: string
  ): ExpressionItemDeleteCancelledAction => ({
    type: EXPRESSION_ITEM_DELETE_CANCELLED,
    id
  }),
  expressionItemDeleteConfirmed: (
    id: string
  ): ExpressionItemDeleteConfirmedAction => ({
    type: EXPRESSION_ITEM_DELETE_CONFIRMED,
    id
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
  pendingDeletionOperatorId?: string;
  expression: ExpressionOperatorWithUuid;
  expressionAsString?: string;
}

export interface StoreState extends StateById<StoreStateById> {}

export const defaultStatePerId: StoreStateById = {
  pendingDeletionOperatorId: undefined,
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
    (state = defaultStatePerId, { operatorId }) => ({
      expression: addItemsToTree(state.expression, operatorId, [
        getNewTerm()
      ]) as ExpressionOperatorWithUuid,
      expressionAsString: toString(state.expression)
    })
  )
  .handleAction<ExpressionOperatorAddedAction>(
    EXPRESSION_OPERATOR_ADDED,
    (state = defaultStatePerId, { operatorId }) => ({
      expression: addItemsToTree(state.expression, operatorId, [
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
  .handleAction<ExpressionItemDeleteRequestedAction>(
    EXPRESSION_ITEM_DELETE_REQUESTED,
    (state = defaultStatePerId, { pendingDeletionOperatorId }) => ({
      ...state,
      pendingDeletionOperatorId
    })
  )
  .handleAction<ExpressionItemDeleteCancelledAction>(
    EXPRESSION_ITEM_DELETE_CANCELLED,
    (state = defaultStatePerId) => ({
      ...state,
      pendingDeletionOperatorId: undefined
    })
  )
  .handleAction<ExpressionItemDeleteConfirmedAction>(
    EXPRESSION_ITEM_DELETE_CONFIRMED,
    (state = defaultStatePerId) => ({
      ...state,
      expression: deleteItemFromTree(
        state.expression,
        state.pendingDeletionOperatorId!
      ) as ExpressionOperatorWithUuid,
      pendingDeletionOperatorId: undefined,
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
