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

import {
  prepareReducerById,
  StateById,
  ActionId
} from "../../../lib/redux-actions-ts";
import { DOC_REFS_COPIED, DocRefsCopied } from "./documentTree";

const PREPARE_DOC_REF_COPY = "PREPARE_DOC_REF_COPY";
const COMPLETE_DOC_REF_COPY = "COMPLETE_DOC_REF_COPY";

export interface PrepareDocRefCopy
  extends ActionId,
    Action<"PREPARE_DOC_REF_COPY"> {
  uuids: Array<string>;
  destinationUuid: string;
}

export interface CompleteDocRefCopy
  extends ActionId,
    Action<"COMPLETE_DOC_REF_COPY"> {}

export interface ActionCreators {
  prepareDocRefCopy: ActionCreator<PrepareDocRefCopy>;
  completeDocRefCopy: ActionCreator<CompleteDocRefCopy>;
}

export const actionCreators: ActionCreators = {
  prepareDocRefCopy: (id, uuids, destinationUuid) => ({
    type: PREPARE_DOC_REF_COPY,
    id,
    uuids,
    destinationUuid
  }),
  completeDocRefCopy: id => ({ type: COMPLETE_DOC_REF_COPY, id })
};

export interface StoreStatePerId {
  isCopying: boolean;
  uuids: Array<string>;
  destinationUuid?: string;
}

export interface StoreState extends StateById<StoreStatePerId> {}

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
export const defaultStatePerId: StoreStatePerId = {
  isCopying: false,
  uuids: [],
  destinationUuid: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<PrepareDocRefCopy>(
    PREPARE_DOC_REF_COPY,
    (state, { uuids, destinationUuid }: PrepareDocRefCopy) => ({
      isCopying: uuids.length > 0,
      uuids,
      destinationUuid
    })
  )
  .handleAction<CompleteDocRefCopy>(
    COMPLETE_DOC_REF_COPY,
    () => defaultStatePerId
  )
  .handleForeignAction<DocRefsCopied>(DOC_REFS_COPIED, () => defaultStatePerId)
  .getReducer();
