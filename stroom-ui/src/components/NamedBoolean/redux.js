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

const namedBooleanCreated = createAction('NAMED_BOOLEAN_CREATED',
    (namedBooleanId) => ({namedBooleanId}));
const setNamedBoolean = createAction('NAMED_BOOLEAN_SET',
    (namedBooleanId, value) => ({namedBooleanId, value}))
const namedBooleanDestroyed = createAction('NAMED_BOOLEAN_DESTROYED',
    (namedBooleanId) => ({namedBooleanId}))

/**
 * The state will be an object, keys are namedBooleanId, values are boolean for isOpen
 */
const defaultnamedBooleanState = {}

const namedBooleanReducer = handleActions({
    [namedBooleanCreated]:
    (state, action) => ({
        ...state,
        [action.payload.namedBooleanId] : false
    }),
    [setNamedBoolean]:
    (state, action) => ({
        ...state,
        [action.payload.namedBooleanId] : action.payload.value
    }),
    [namedBooleanDestroyed]:
    (state, action) => ({
        ...state,
        [action.payload.namedBooleanId] : undefined
    })
}, defaultnamedBooleanState);

export {
    namedBooleanReducer,
    namedBooleanCreated,
    setNamedBoolean,
    namedBooleanDestroyed
}