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

export const XSLT_RECEIVED = "XSLT_RECEIVED";
export const XSLT_UPDATED = "XSLT_UPDATED";
export const XSLT_SAVED = "XSLT_SAVED";

export interface XsltAction extends ActionId {
  xsltData: string;
  isDirty: boolean;
}

export interface XsltReceivedAction
  extends Action<"XSLT_RECEIVED">,
    XsltAction {}
export interface XsltUpdatedAction extends Action<"XSLT_UPDATED">, XsltAction {}
export interface XsltSavedAction extends Action<"XSLT_SAVED">, ActionId {}

export const actionCreators = {
  xsltReceived: (id: string, xsltData: string): XsltReceivedAction => ({
    type: XSLT_RECEIVED,
    id,
    xsltData,
    isDirty: false
  }),
  xsltUpdated: (id: string, xsltData: string): XsltUpdatedAction => ({
    type: XSLT_UPDATED,
    id,
    xsltData,
    isDirty: true
  }),
  xsltSaved: (id: string): XsltSavedAction => ({ type: XSLT_SAVED, id })
};

export interface StoreStateById {
  isDirty: boolean;
  xsltData?: string;
}

export type StoreState = StateById<StoreStateById>;

export const defaultStatePerId: StoreStateById = {
  isDirty: false,
  xsltData: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleActions<XsltAction>(
    [XSLT_RECEIVED, XSLT_UPDATED],
    (state, { isDirty, xsltData }) => ({
      isDirty,
      xsltData
    })
  )
  .handleAction<XsltSavedAction>(XSLT_SAVED, state => ({
    ...state,
    isDirty: false
  }))
  .getReducer();
