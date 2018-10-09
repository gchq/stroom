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
} from "../../../lib/redux-actions-ts";
import { DOC_REFS_COPIED, DocRefsCopiedAction } from "./documentTree";

const PREPARE_DOC_REF_COPY = "PREPARE_DOC_REF_COPY";
const COMPLETE_DOC_REF_COPY = "COMPLETE_DOC_REF_COPY";

export interface PrepareDocRefCopyAction
  extends ActionId,
    Action<"PREPARE_DOC_REF_COPY"> {
  uuids: Array<string>;
  destinationUuid: string;
}

export interface CompleteDocRefCopyAction
  extends ActionId,
    Action<"COMPLETE_DOC_REF_COPY"> {}

export const actionCreators = {
  prepareDocRefCopy: (
    id: string,
    uuids: Array<string>,
    destinationUuid: string
  ): PrepareDocRefCopyAction => ({
    type: PREPARE_DOC_REF_COPY,
    id,
    uuids,
    destinationUuid
  }),
  completeDocRefCopy: (id: string): CompleteDocRefCopyAction => ({
    type: COMPLETE_DOC_REF_COPY,
    id
  })
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
  .handleAction<PrepareDocRefCopyAction>(
    PREPARE_DOC_REF_COPY,
    (state, { uuids, destinationUuid }: PrepareDocRefCopyAction) => ({
      isCopying: uuids.length > 0,
      uuids,
      destinationUuid
    })
  )
  .handleAction<CompleteDocRefCopyAction>(
    COMPLETE_DOC_REF_COPY,
    () => defaultStatePerId
  )
  .handleForeignAction<DocRefsCopiedAction>(
    DOC_REFS_COPIED,
    () => defaultStatePerId
  )
  .getReducer();
