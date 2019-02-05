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
  StateById,
  ActionId
} from "../../lib/redux-actions-ts";

export const INDEX_RECEIVED = "INDEX_RECEIVED";
export const INDEX_UPDATED = "INDEX_UPDATED";
export const INDEX_SAVED = "INDEX_SAVED";

export interface IndexAction extends ActionId {
  indexData: string;
  isDirty: boolean;
}

export interface IndexReceivedAction
  extends Action<"INDEX_RECEIVED">,
    IndexAction {}
export interface IndexUpdatedAction
  extends Action<"INDEX_UPDATED">,
    IndexAction {}
export interface IndexSavedAction extends Action<"INDEX_SAVED">, ActionId {}

export const actionCreators = {
  indexReceived: (id: string, indexData: string): IndexReceivedAction => ({
    type: INDEX_RECEIVED,
    id,
    indexData,
    isDirty: false
  }),
  indexUpdated: (id: string, indexData: string): IndexUpdatedAction => ({
    type: INDEX_UPDATED,
    id,
    indexData,
    isDirty: true
  }),
  indexSaved: (id: string): IndexSavedAction => ({ type: INDEX_SAVED, id })
};

export interface StoreStateById {
  isDirty: boolean;
  indexData?: string;
}

export type StoreState = StateById<StoreStateById>;

export const defaultStatePerId: StoreStateById = {
  isDirty: false,
  indexData: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleActions<IndexAction>(
    [INDEX_RECEIVED, INDEX_UPDATED],
    (state, { isDirty, indexData }) => ({
      isDirty,
      indexData
    })
  )
  .handleAction<IndexSavedAction>(INDEX_SAVED, state => ({
    ...state,
    isDirty: false
  }))
  .getReducer();
