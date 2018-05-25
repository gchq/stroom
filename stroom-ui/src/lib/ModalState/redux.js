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

const modalCreated = createAction('MODAL_CREATED',
    (modalId) => ({modalId}));
const modalOpened = createAction('MODAL_OPENED',
    (modalId) => ({modalId}))
const modalClosed = createAction('MODAL_CLOSED',
    (modalId) => ({modalId}))
const modalDestroyed = createAction('MODAL_DESTROYED',
    (modalId) => ({modalId}))

/**
 * The state will be an object, keys are modalId, values are boolean for isOpen
 */
const defaultModalState = {}

const modalReducer = handleActions({
    [modalCreated]:
    (state, action) => ({
        ...state,
        [action.payload.modalId] : false
    }),
    [modalOpened]:
    (state, action) => ({
        ...state,
        [action.payload.modalId] : true
    }),
    [modalClosed]:
    (state, action) => ({
        ...state,
        [action.payload.modalId] : false
    }),
    [modalDestroyed]:
    (state, action) => ({
        ...state,
        [action.payload.modalId] : undefined
    })
}, defaultModalState);

export {
    modalReducer,
    modalCreated,
    modalOpened,
    modalClosed,
    modalDestroyed
}