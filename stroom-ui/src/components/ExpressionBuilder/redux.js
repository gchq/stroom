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
import { createAction, handleActions, combineActions } from 'redux-actions';

import { 
    moveItemInTree,
    iterateNodes,
    assignRandomUuids,
    updateItemInTree,
    addItemToTree,
    deleteItemFromTree,
    stripUuids
} from 'lib/treeUtils';

import {
    docRefPicked
} from 'components/DocExplorer'

// Expressions

const expressionChanged = createAction('EXPRESSION_CHANGED',
    (expressionId, expression) => ({ expressionId, expression }));
const expressionTermAdded = createAction('EXPRESSION_TERM_ADDED',
    (expressionId, operatorId) => ({expressionId, operatorId}));
const expressionOperatorAdded = createAction('EXPRESSION_OPERATOR_ADDED',
    (expressionId, operatorId) => ({expressionId, operatorId}));
const expressionItemUpdated = createAction('EXPRESSION_ITEM_UPDATED',
    (expressionId, itemId, updates) => ({expressionId, itemId, updates}))
const expressionItemDeleted = createAction('EXPRESSION_ITEM_DELETED',
    (expressionId, itemId) => ({expressionId, itemId}));
const expressionItemMoved = createAction('EXPRESSION_ITEM_MOVED',
    (expressionId, itemToMove, destination) => ({expressionId, itemToMove, destination}));

// expressions, keyed on ID, there may be several expressions on a page
const defaultExpressionState = {};

const NEW_TERM = {
    "type" : "term",
    "enabled" : true
}

const NEW_OPERATOR = {
    "type" : "operator",
    "op" : "AND",
    "enabled" : true,
    "children": []
};

const splitDictionaryTermId = (value) => {
    let p = value.split('_');
    return {
        expressionId : p[0],
        termUuid : p[1]
    }
}

const joinDictionaryTermId = (expressionId, termUuid) => (expressionId + '_' + termUuid)

const expressionReducer = handleActions({
    // Expression Changed
    [expressionChanged]:
    (state, action) => ({
        ...state,
        [action.payload.expressionId] : assignRandomUuids(action.payload.expression)
    }),

    // Expression Term Added
    [expressionTermAdded]:
    (state, action) => ({
        ...state,
        [action.payload.expressionId] : addItemToTree(
                state[action.payload.expressionId],
                action.payload.operatorId,
                NEW_TERM
            )
    }),

    // Expression Operator Added
    [expressionOperatorAdded]:
    (state, action) => ({
        ...state,
        [action.payload.expressionId] : addItemToTree(
                state[action.payload.expressionId], 
                action.payload.operatorId, 
                NEW_OPERATOR
            )
    }),

    // Expression Term Updated
    [expressionItemUpdated]:
    (state, action) => ({
        ...state,
        [action.payload.expressionId] : updateItemInTree(
                state[action.payload.expressionId], 
                action.payload.itemId, 
                action.payload.updates
            )
    }),

    // Expression Item Deleted
    [expressionItemDeleted]:
    (state, action) => ({
        ...state,
        [action.payload.expressionId] : deleteItemFromTree(
                state[action.payload.expressionId], 
                action.payload.itemId
            )
    }),

    // Expression Item Moved
    [expressionItemMoved]:
    (state, action) => ({
        ...state,
        [action.payload.expressionId] : moveItemInTree(
                state[action.payload.expressionId],
                action.payload.itemToMove, 
                action.payload.destination
            )
    }),

    // Doc Ref Picked
    [docRefPicked]:
    (state, action) => {
        let {
            expressionId,
            termUuid
        } = splitDictionaryTermId(action.payload.pickerId);

        return {
            ...state,
            [expressionId] : updateItemInTree(
                    state[expressionId], 
                    termUuid, 
                    {
                        value: undefined,
                        dictionary: action.payload.docRef
                    }
                )
        }
    }
}, defaultExpressionState);

export {
    expressionChanged,
    expressionTermAdded,
    expressionOperatorAdded,
    expressionItemUpdated,
    expressionItemDeleted,
    expressionItemMoved,
    expressionReducer,
    joinDictionaryTermId
}