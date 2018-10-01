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

import {
  prepareReducerById,
  ActionId,
  StateById
} from "../../lib/redux-actions-ts";
import { Dictionary } from "../../types";

export const DICTIONARY_RECEIVED = "DICTIONARY_RECEIVED";
export const DICTIONARY_UPDATED = "DICTIONARY_UPDATED";
export const DICTIONARY_SAVED = "DICTIONARY_SAVED";

export interface DictionaryEditorAction extends ActionId {
  dictionary: Dictionary;
  isDirty: boolean;
}

export interface DictionaryReceivedAction
  extends Action<"DICTIONARY_RECEIVED">,
    DictionaryEditorAction {}
export interface DictionaryUpdatedAction
  extends Action<"DICTIONARY_UPDATED">,
    DictionaryEditorAction {}
export interface DictionarySavedAction
  extends Action<"DICTIONARY_SAVED">,
    ActionId {}

export const actionCreators = {
  dictionaryReceived: (
    id: string,
    dictionary: Dictionary
  ): DictionaryReceivedAction => ({
    type: DICTIONARY_RECEIVED,
    id,
    dictionary,
    isDirty: false
  }),
  dictionaryUpdated: (
    id: string,
    dictionary: Dictionary
  ): DictionaryUpdatedAction => ({
    type: DICTIONARY_UPDATED,
    id,
    dictionary,
    isDirty: true
  }),
  dictionarySaved: (id: string): DictionarySavedAction => ({
    type: DICTIONARY_SAVED,
    id
  })
};

export interface StoreStatePerId {
  isDirty: boolean;
  dictionary?: Dictionary;
}

export type StoreState = StateById<StoreStatePerId>;

export const defaultStatePerId: StoreStatePerId = {
  isDirty: false,
  dictionary: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleActions<DictionaryEditorAction>(
    [DICTIONARY_RECEIVED, DICTIONARY_UPDATED],
    (_, { isDirty, dictionary }) => ({ isDirty, dictionary })
  )
  .handleAction<DictionarySavedAction>(DICTIONARY_SAVED, state => ({
    ...state,
    isDirty: false
  }))
  .getReducer();
