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
} from "../../../lib/redux-actions-ts";
import { DOC_REF_RENAMED, DocRefRenamedAction } from "./documentTree";
import { DocRefType } from "../../../types";

export const PREPARE_DOC_REF_RENAME = "PREPARE_DOC_REF_RENAME";
export const COMPLETE_DOC_REF_RENAME = "COMPLETE_DOC_REF_RENAME";

export interface PrepareDocRefRenameAction
  extends ActionId,
    Action<"PREPARE_DOC_REF_RENAME"> {
  docRef: DocRefType;
}

export interface CompleteDocRefRenameAction
  extends ActionId,
    Action<"COMPLETE_DOC_REF_RENAME"> {}

export const actionCreators = {
  prepareDocRefRename: (
    id: string,
    docRef: DocRefType
  ): PrepareDocRefRenameAction => ({
    type: PREPARE_DOC_REF_RENAME,
    id,
    docRef
  }),
  completeDocRefRename: (id: string): CompleteDocRefRenameAction => ({
    type: COMPLETE_DOC_REF_RENAME,
    id
  })
};

export interface StoreStateById {
  isRenaming: boolean;
  docRef?: DocRefType;
  name: string;
}

export interface StoreState extends StateById<StoreStateById> {}

export const defaultStatePerId: StoreStateById = {
  isRenaming: false,
  docRef: undefined,
  name: ""
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<PrepareDocRefRenameAction>(
    PREPARE_DOC_REF_RENAME,
    (_, { docRef }) => ({ isRenaming: true, docRef, name: "" })
  )
  .handleAction<CompleteDocRefRenameAction>(
    COMPLETE_DOC_REF_RENAME,
    () => defaultStatePerId
  )
  .handleForeignAction<DocRefRenamedAction>(
    DOC_REF_RENAMED,
    () => defaultStatePerId
  )
  .getReducer();
