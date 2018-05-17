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

// Expressions

const expressionChanged = createAction('EXPRESSION_CHANGED',
    (id, expression) => ({ id, expression }));
const expressionTermAdded = createAction('EXPRESSION_TERM_ADDED',
    (id, operatorId) => ({id, operatorId}));
const expressionOperatorAdded = createAction('EXPRESSION_OPERATOR_ADDED',
    (id, operatorId) => ({id, operatorId}));
const expressionItemUpdated = createAction('EXPRESSION_ITEM_UPDATED',
    (id, itemId, updates) => ({id, itemId, updates}))
const expressionItemDeleted = createAction('EXPRESSION_ITEM_DELETED',
    (id, itemId) => ({id, itemId}));
const expressionItemMoved = createAction('EXPRESSION_ITEM_MOVED',
    (id, itemToMove, destination) => ({id, itemToMove, destination}));

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

const expressionReducer = handleActions({
        // Expression Changed
        [expressionChanged]:
        (state, action) => ({
            ...state,
            [action.payload.id] : assignRandomUuids(action.payload.expression)
        }),

        // Expression Term Added
        [expressionTermAdded]:
        (state, action) => ({
            ...state,
            [action.payload.id] : addItemToTree(
                    state[action.payload.id],
                    action.payload.operatorId,
                    NEW_TERM
                )
        }),

        // Expression Operator Added
        [expressionOperatorAdded]:
        (state, action) => ({
            ...state,
            [action.payload.id] : addItemToTree(
                    state[action.payload.id], 
                    action.payload.operatorId, 
                    NEW_OPERATOR
                )
        }),

        // Expression Term Updated
        [expressionItemUpdated]:
        (state, action) => ({
            ...state,
            [action.payload.id] : updateItemInTree(
                    state[action.payload.id], 
                    action.payload.itemId, 
                    action.payload.updates
                )
        }),

        // Expression Item Deleted
        [expressionItemDeleted]:
        (state, action) => ({
            ...state,
            [action.payload.id] : deleteItemFromTree(
                    state[action.payload.id], 
                    action.payload.itemId
                )
        }),

        // Expression Item Moved
        [expressionItemMoved]:
        (state, action) => ({
            ...state,
            [action.payload.id] : moveItemInTree(
                    state[action.payload.id],
                    action.payload.itemToMove, 
                    action.payload.destination
                )
        })
    },
    defaultExpressionState
);

export {
    expressionChanged,
    expressionTermAdded,
    expressionOperatorAdded,
    expressionItemUpdated,
    expressionItemDeleted,
    expressionItemMoved,
    expressionReducer
}