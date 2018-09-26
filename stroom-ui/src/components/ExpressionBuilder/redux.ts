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
import { Action, ActionCreator } from "redux";
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
  ExpressionOperator,
  ExpressionTerm,
  ExpressionItem
} from "../../types";

export const EXPRESSION_EDITOR_CREATED = "EXPRESSION_EDITOR_CREATED";
export const EXPRESSION_EDITOR_DESTROYED = "EXPRESSION_EDITOR_DESTROYED";
export const EXPRESSION_SET_EDITABLE_BY_USER =
  "EXPRESSION_SET_EDITABLE_BY_USER";
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
export interface ExpressionSetEditableByUserAction
  extends Action<"EXPRESSION_SET_EDITABLE_BY_USER">,
    ActionId {
  isEditableUserSet: boolean;
}
export interface ExpressionChangedAction
  extends Action<"EXPRESSION_CHANGED">,
    ActionId {
  expression: ExpressionOperator;
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
  updates: ExpressionOperator | ExpressionTerm;
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
  itemToMove: ExpressionItem;
  destination: ExpressionItem;
}

export interface ActionCreators {
  expressionEditorCreated: ActionCreator<ExpressionEditorCreatedAction>;
  expressionEditorDestroyed: ActionCreator<ExpressionEditorDestroyedAction>;
  expressionSetEditableByUser: ActionCreator<ExpressionSetEditableByUserAction>;
  expressionChanged: ActionCreator<ExpressionChangedAction>;
  expressionTermAdded: ActionCreator<ExpressionTermAddedAction>;
  expressionOperatorAdded: ActionCreator<ExpressionOperatorAddedAction>;
  expressionItemUpdated: ActionCreator<ExpressionItemUpdatedAction>;
  expressionItemDeleteRequested: ActionCreator<
    ExpressionItemDeleteRequestedAction
  >;
  expressionItemDeleteCancelled: ActionCreator<
    ExpressionItemDeleteCancelledAction
  >;
  expressionItemDeleteConfirmed: ActionCreator<
    ExpressionItemDeleteConfirmedAction
  >;
  expressionItemMoved: ActionCreator<ExpressionItemMovedAction>;
}

export const actionCreators: ActionCreators = {
  expressionEditorCreated: id => ({
    type: EXPRESSION_EDITOR_CREATED,
    id
  }),
  expressionEditorDestroyed: id => ({
    type: EXPRESSION_EDITOR_DESTROYED,
    id
  }),
  expressionSetEditableByUser: (id, isEditableUserSet) => ({
    type: EXPRESSION_SET_EDITABLE_BY_USER,
    id,
    isEditableUserSet
  }),
  expressionChanged: (id, expression) => ({
    type: EXPRESSION_CHANGED,
    id,
    expression
  }),
  expressionTermAdded: (id, operatorId) => ({
    type: EXPRESSION_TERM_ADDED,
    id,
    operatorId
  }),
  expressionOperatorAdded: (id, operatorId) => ({
    type: EXPRESSION_OPERATOR_ADDED,
    id,
    operatorId
  }),
  expressionItemUpdated: (id, itemId, updates) => ({
    type: EXPRESSION_ITEM_UPDATED,
    id,
    itemId,
    updates
  }),
  expressionItemDeleteRequested: (id, pendingDeletionOperatorId) => ({
    type: EXPRESSION_ITEM_DELETE_REQUESTED,
    id,
    pendingDeletionOperatorId
  }),
  expressionItemDeleteCancelled: id => ({
    type: EXPRESSION_ITEM_DELETE_CANCELLED,
    id
  }),
  expressionItemDeleteConfirmed: id => ({
    type: EXPRESSION_ITEM_DELETE_CONFIRMED,
    id
  }),
  expressionItemMoved: (id, itemToMove, destination) => ({
    type: EXPRESSION_ITEM_MOVED,
    id,
    itemToMove,
    destination
  })
};

const NEW_TERM: ExpressionTerm = {
  uuid: uuidv4(),
  type: "term",
  condition: "EQUALS",
  enabled: true
};

const NEW_OPERATOR: ExpressionOperator = {
  uuid: uuidv4(),
  type: "operator",
  op: "AND",
  enabled: true,
  children: []
};

export interface StoreStateById {
  pendingDeletionOperatorId?: string;
  expression: ExpressionOperator;
  expressionAsString?: string;
}

export interface StoreState extends StateById<StoreStateById> {}

export const defaultStatePerId: StoreStateById = {
  pendingDeletionOperatorId: undefined,
  expression: NEW_OPERATOR
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<ExpressionChangedAction>(
    EXPRESSION_CHANGED,
    (state, { expression }) => ({
      ...state,
      expression: assignRandomUuids(expression) as ExpressionOperator,
      expressionAsString: toString(expression)
    })
  )
  .handleAction<ExpressionTermAddedAction>(
    EXPRESSION_TERM_ADDED,
    (state = defaultStatePerId, { operatorId }) => ({
      expression: addItemsToTree(state.expression, operatorId, [
        NEW_TERM
      ]) as ExpressionOperator,
      expressionAsString: toString(state.expression)
    })
  )
  .handleAction<ExpressionOperatorAddedAction>(
    EXPRESSION_OPERATOR_ADDED,
    (state = defaultStatePerId, { operatorId }) => ({
      expression: addItemsToTree(state.expression, operatorId, [
        NEW_OPERATOR
      ]) as ExpressionOperator,
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
      ) as ExpressionOperator,
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
      ) as ExpressionOperator,
      pendingDeletionOperatorId: undefined,
      expressionAsString: toString(state.expression)
    })
  )
  .handleAction<ExpressionItemMovedAction>(
    EXPRESSION_ITEM_MOVED,
    (state = defaultStatePerId, { destination, itemToMove }) => ({
      expression: moveItemsInTree(state.expression, destination, [
        itemToMove
      ]) as ExpressionOperator,
      expressionAsString: toString(state.expression)
    })
  )
  .getReducer();
