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
import { DocRefTypeList } from "src/types";

export type StoreState = DocRefTypeList;

export interface DocRefTypesReceived extends Action<"DOC_REF_TYPES_RECEIVED"> {
  docRefTypes: DocRefTypeList;
}

const DOC_REF_TYPES_RECEIVED = "DOC_REF_TYPES_RECEIVED";

export const actionCreators = {
  docRefTypesReceived: (docRefTypes: DocRefTypeList): DocRefTypesReceived => ({
    type: DOC_REF_TYPES_RECEIVED,
    docRefTypes
  })
};

const defaultState: StoreState = [];

export const reducer = prepareReducer(defaultState)
  .handleAction<DocRefTypesReceived>(
    DOC_REF_TYPES_RECEIVED,
    (state: StoreState, { docRefTypes }) => docRefTypes
  )
  .getReducer();
