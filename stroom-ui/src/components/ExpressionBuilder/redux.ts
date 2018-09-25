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

import { prepareReducer } from "../../lib/redux-actions-ts";

import {
  assignRandomUuids,
  updateItemInTree,
  addItemsToTree,
  deleteItemFromTree,
  moveItemsInTree
} from "../../lib/treeUtils";

import { toString } from "./expressionBuilderUtils";

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

// Expression Editors
const actionCreators = createActions({
  EXPRESSION_EDITOR_CREATED: expressionId => ({
    expressionId
  }),
  EXPRESSION_EDITOR_DESTROYED: expressionId => ({
    expressionId
  }),
  EXPRESSION_SET_EDITABLE_BY_USER: (expressionId, isEditableUserSet) => ({
    expressionId,
    isEditableUserSet
  }),
  EXPRESSION_CHANGED: (expressionId, expression) => ({
    expressionId,
    expression
  }),
  EXPRESSION_TERM_ADDED: (expressionId, operatorId) => ({
    expressionId,
    operatorId
  }),
  EXPRESSION_OPERATOR_ADDED: (expressionId, operatorId) => ({
    expressionId,
    operatorId
  }),
  EXPRESSION_ITEM_UPDATED: (expressionId, itemId, updates) => ({
    expressionId,
    itemId,
    updates
  }),
  EXPRESSION_ITEM_DELETE_REQUESTED: (
    expressionId,
    pendingDeletionOperatorId
  ) => ({
    expressionId,
    pendingDeletionOperatorId
  }),
  EXPRESSION_ITEM_DELETE_CANCELLED: expressionId => ({ expressionId }),
  EXPRESSION_ITEM_DELETE_CONFIRMED: expressionId => ({ expressionId }),
  EXPRESSION_ITEM_MOVED: (expressionId, itemToMove, destination) => ({
    expressionId,
    itemToMove,
    destination
  })
});

const NEW_TERM = {
  type: "term",
  condition: "EQUALS",
  enabled: true
};

const NEW_OPERATOR = {
  type: "operator",
  op: "AND",
  enabled: true,
  children: []
};

const defaultStatePerExpression = {
  pendingDeletionOperatorId: undefined,
  expression: NEW_OPERATOR
};

// expressions, keyed on ID, there may be several expressions on a page
const defaultState = {};

const byExpressionId = createActionHandlersPerId(
  ({ payload }) => payload.expressionId,
  defaultStatePerExpression
);

const reducer = handleActions(
  byExpressionId({
    // Expression Changed
    EXPRESSION_CHANGED: (state, { payload: { expression } }) => ({
      expression: assignRandomUuids(expression),
      expressionAsString: toString(expression)
    }),

    // Expression Term Added
    EXPRESSION_TERM_ADDED: (
      state,
      { payload: { operatorId } },
      { expression }
    ) => ({
      expression: addItemsToTree(expression, operatorId, [NEW_TERM]),
      expressionAsString: toString(expression)
    }),

    // Expression Operator Added
    EXPRESSION_OPERATOR_ADDED: (
      state,
      { payload: { operatorId } },
      { expression }
    ) => ({
      expression: addItemsToTree(expression, operatorId, [NEW_OPERATOR]),
      expressionAsString: toString(expression)
    }),

    // Expression Term Updated
    EXPRESSION_ITEM_UPDATED: (
      state,
      { payload: { itemId, updates } },
      { expression }
    ) => ({
      expression: updateItemInTree(expression, itemId, updates),
      expressionAsString: toString(expression)
    }),

    EXPRESSION_ITEM_DELETE_REQUESTED: (
      state,
      { payload: { pendingDeletionOperatorId } }
    ) => ({
      pendingDeletionOperatorId
    }),

    EXPRESSION_ITEM_DELETE_CANCELLED: (state, action, current) => ({
      pendingDeletionOperatorId: undefined
    }),

    // Expression Item Deleted
    EXPRESSION_ITEM_DELETE_CONFIRMED: (
      state,
      action,
      { expression, pendingDeletionOperatorId }
    ) => ({
      expression: deleteItemFromTree(expression, pendingDeletionOperatorId),
      pendingDeletionOperatorId: undefined,
      expressionAsString: toString(expression)
    }),

    // Expression Item Moved
    EXPRESSION_ITEM_MOVED: (
      state,
      { payload: { destination, itemToMove } },
      { expression }
    ) => ({
      expression: moveItemsInTree(expression, destination, [itemToMove]),
      expressionAsString: toString(expression)
    })
  }),
  defaultState
);

export { actionCreators, reducer };
