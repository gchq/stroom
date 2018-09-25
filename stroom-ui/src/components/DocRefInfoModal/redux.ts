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
import { DocRefInfoType, DocRefType } from "../../types";

export const DOC_REF_INFO_RECEIVED = "DOC_REF_INFO_RECEIVED";
export const DOC_REF_INFO_OPENED = "DOC_REF_INFO_OPENED";
export const DOC_REF_INFO_CLOSED = "DOC_REF_INFO_CLOSED";

export interface DocRefInfoReceivedAction
  extends Action<"DOC_REF_INFO_RECEIVED"> {
  docRefInfo: DocRefInfoType;
}
export interface DocRefInfoOpenedAction extends Action<"DOC_REF_INFO_OPENED"> {
  docRef: DocRefType;
}
export interface DocRefInfoClosedAction extends Action<"DOC_REF_INFO_CLOSED"> {}

export interface ActionCreators {
  docRefInfoReceived: ActionCreator<DocRefInfoReceivedAction>;
  docRefInfoOpened: ActionCreator<DocRefInfoOpenedAction>;
  docRefInfoClosed: ActionCreator<DocRefInfoClosedAction>;
}

export const actionCreators: ActionCreators = {
  docRefInfoReceived: docRefInfo => ({
    type: DOC_REF_INFO_RECEIVED,
    docRefInfo
  }),
  docRefInfoOpened: docRef => ({
    type: DOC_REF_INFO_OPENED,
    docRef
  }),
  docRefInfoClosed: () => ({
    type: DOC_REF_INFO_CLOSED
  })
};

export interface StoreState {
  isOpen: boolean;
  docRefInfo?: DocRefInfoType;
  docRefInfoCache: { [s: string]: DocRefInfoType };
}

// The state will contain the current doc ref for which information is being shown,
// plus a map of all the infos retrieved thus far, keyed on their UUID
export const defaultState: StoreState = {
  isOpen: false,
  docRefInfo: undefined,
  docRefInfoCache: {} // keyed on UUID
};

export const reducer = prepareReducer(defaultState)
  .handleAction<DocRefInfoReceivedAction>(
    DOC_REF_INFO_RECEIVED,
    (state = defaultState, { docRefInfo }) => ({
      ...state,
      docRefInfo,
      docRefInfoCache: {
        ...state.docRefInfoCache,
        [docRefInfo.docRef.uuid]: docRefInfo
      }
    })
  )
  .handleAction<DocRefInfoOpenedAction>(
    DOC_REF_INFO_OPENED,
    (state = defaultState, { docRef: { uuid } }) => ({
      ...state,
      isOpen: true,
      docRefInfo: state.docRefInfoCache[uuid]
    })
  )
  .handleAction<DocRefInfoClosedAction>(
    DOC_REF_INFO_CLOSED,
    (state = defaultState) => ({
      ...state,
      isOpen: false
    })
  )
  .getReducer();
