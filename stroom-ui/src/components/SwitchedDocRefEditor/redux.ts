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

import { prepareReducer } from "../../lib/redux-actions-ts";
import { DocRefType } from "../../types";

export const DOC_REF_OPENED = "DOC_REF_OPENED";

export interface DocRefOpenedAction extends Action<"DOC_REF_OPENED"> {
  docRef: DocRefType;
}

export const actionCreators = {
  docRefOpened: (docRef: DocRefType): DocRefOpenedAction => ({
    type: DOC_REF_OPENED,
    docRef
  })
};

export type StoreState = Array<DocRefType>;

export const defaultState: StoreState = [];

export const reducer = prepareReducer(defaultState)
  .handleAction<DocRefOpenedAction>(
    DOC_REF_OPENED,
    (state: StoreState, { docRef }: DocRefOpenedAction) =>
      [docRef].concat(state.filter(d => d.uuid !== docRef.uuid))
  )
  .getReducer();
