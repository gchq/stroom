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

import { deleteItemFromObject } from 'lib/treeUtils';

const lineContainerCreated = createAction('LINE_CONTAINER_CREATED',
    (lineContextId) => ({lineContextId}));
const lineContainerDestroyed = createAction('LINE_CONTAINER_DESTROYED',
    (lineContextId) => ({lineContextId}));

const lineCreated = createAction('LINE_CREATED',
    (lineContextId, lineId, lineType, fromId, toId) => ({
        lineContextId, 
        lineId,
        lineType,
        fromId,
        toId
    }));
const lineDestroyed = createAction('LINE_DESTROYED',
    (lineContextId, lineId) => ({lineContextId, lineId}));

// State will be an object
const defaultState = {};

const lineContainerReducer = handleActions({
    [lineContainerCreated]:
    (state, action) => ({
        ...state,
        [action.payload.lineContextId] : {}
    }),

    [lineContainerDestroyed]:
    (state, action) => (deleteItemFromObject(...state, action.payload.lineContextId)),

    [lineCreated]:
    (state, action) => ({
        ...state,
        [action.payload.lineContextId] : {
            ...state[action.payload.lineContextId],
            [action.payload.lineId]: {
                lineType : action.payload.lineType,
                fromId : action.payload.fromId,
                toId : action.payload.toId
            }
        }
    }),

    [lineDestroyed]:
    (state, action) => ({
        ...state,
        [action.payload.lineContextId] : deleteItemFromObject(state[action.payload.lineContextId], action.payload.lineId)
    })
}, defaultState);

export {
    lineContainerReducer,
    lineCreated,
    lineDestroyed,
    lineContainerCreated,
    lineContainerDestroyed
}