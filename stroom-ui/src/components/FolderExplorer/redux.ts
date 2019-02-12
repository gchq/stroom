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
import { updateItemInTree } from "../../lib/treeUtils";
import { DocRefType, DocRefTree, DocRefInfoType } from "../../types";

export const DOC_TREE_RECEIVED = "DOC_TREE_RECEIVED";
export const DOC_REFS_MOVED = "DOC_REFS_MOVED";
export const DOC_REFS_COPIED = "DOC_REFS_COPIED";
export const DOC_REFS_DELETED = "DOC_REFS_DELETED";
export const DOC_REF_CREATED = "DOC_REF_CREATED";
export const DOC_REF_RENAMED = "DOC_REF_RENAMED";
export const DOC_REF_INFO_RECEIVED = "DOC_REF_INFO_RECEIVED";

export interface StoreState {
  waitingForTree?: boolean;
  documentTree: DocRefTree;
  docRefInfoByUuid: {
    [s: string]: DocRefInfoType;
  };
}

export interface DocTreeReceived extends Action<"DOC_TREE_RECEIVED"> {
  documentTree: DocRefTree;
}

export interface UpdateTreeAction {
  updatedTree: DocRefTree;
}
export interface DocRefsMovedAction
  extends Action<"DOC_REFS_MOVED">,
    UpdateTreeAction {
  docRefs: Array<DocRefType>;
  destination: DocRefType;
}
export interface DocRefsCopiedAction
  extends Action<"DOC_REFS_COPIED">,
    UpdateTreeAction {
  docRefs: Array<DocRefType>;
  destination: DocRefType;
}
export interface DocRefsDeletedAction
  extends Action<"DOC_REFS_DELETED">,
    UpdateTreeAction {
  docRefs: Array<DocRefType>;
}
export interface DocRefCreatedAction
  extends Action<"DOC_REF_CREATED">,
    UpdateTreeAction {}
export interface DocRefRenamedAction extends Action<"DOC_REF_RENAMED"> {
  docRef: DocRefType;
  name: string;
  resultDocRef: DocRefType;
}
export interface DocRefInfoReceivedAction
  extends Action<"DOC_REF_INFO_RECEIVED"> {
  docRefInfo: DocRefInfoType;
}

export const actionCreators = {
  docTreeReceived: (documentTree: DocRefTree): DocTreeReceived => ({
    type: DOC_TREE_RECEIVED,
    documentTree
  }),
  docRefsMoved: (
    docRefs: Array<DocRefType>,
    destination: DocRefType,
    updatedTree: DocRefTree
  ): DocRefsMovedAction => ({
    type: DOC_REFS_MOVED,
    docRefs,
    destination,
    updatedTree
  }),
  docRefsCopied: (
    docRefs: Array<DocRefType>,
    destination: DocRefType,
    updatedTree: DocRefTree
  ): DocRefsCopiedAction => ({
    type: DOC_REFS_COPIED,
    docRefs,
    destination,
    updatedTree
  }),
  docRefsDeleted: (
    docRefs: Array<DocRefType>,
    updatedTree: DocRefTree
  ): DocRefsDeletedAction => ({
    type: DOC_REFS_DELETED,
    docRefs,
    updatedTree
  }),
  docRefCreated: (updatedTree: DocRefTree): DocRefCreatedAction => ({
    type: DOC_REF_CREATED,
    updatedTree
  }),
  docRefRenamed: (
    docRef: DocRefType,
    name: string,
    resultDocRef: DocRefType
  ): DocRefRenamedAction => ({
    type: DOC_REF_RENAMED,
    docRef,
    name,
    resultDocRef
  }),
  docRefInfoReceived: (
    docRefInfo: DocRefInfoType
  ): DocRefInfoReceivedAction => ({
    type: DOC_REF_INFO_RECEIVED,
    docRefInfo
  })
};

const defaultState: StoreState = {
  docRefInfoByUuid: {},
  waitingForTree: true,
  documentTree: {
    uuid: "none",
    type: "System",
    name: "None"
  }
};

export const reducer = prepareReducer(defaultState)
  .handleAction<DocTreeReceived>(DOC_TREE_RECEIVED, (_, { documentTree }) => ({
    waitingForTree: false,
    documentTree,
    docRefInfoByUuid: {}
  }))
  .handleAction<DocRefRenamedAction>(
    DOC_REF_RENAMED,
    (state = defaultState, { docRef, resultDocRef }) => ({
      ...state,
      documentTree: updateItemInTree(
        state.documentTree,
        docRef.uuid,
        resultDocRef
      )
    })
  )
  .handleActions<UpdateTreeAction>(
    [DOC_REFS_MOVED, DOC_REFS_COPIED, DOC_REFS_DELETED, DOC_REF_CREATED],
    (state = defaultState, { updatedTree }) => ({
      ...state,
      documentTree: updatedTree
    })
  )
  .handleAction<DocRefInfoReceivedAction>(
    DOC_REF_INFO_RECEIVED,
    (state = defaultState, { docRefInfo }) => ({
      ...state,
      docRefInfoByUuid: {
        ...state.docRefInfoByUuid,
        [docRefInfo.docRef.uuid]: docRefInfo
      }
    })
  )
  .getReducer();
