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

import { prepareReducer } from "../../../lib/redux-actions-ts";
import { updateItemInTree } from "../../../lib/treeUtils";
import { DocRefType, DocRefTree } from "../../../types";

export const DOC_TREE_RECEIVED = "DOC_TREE_RECEIVED";
export const DOC_REFS_MOVED = "DOC_REFS_MOVED";
export const DOC_REFS_COPIED = "DOC_REFS_COPIED";
export const DOC_REFS_DELETED = "DOC_REFS_DELETED";
export const DOC_REF_CREATED = "DOC_REF_CREATED";
export const DOC_REF_RENAMED = "DOC_REF_RENAMED";

export interface StoreState extends DocRefTree {
  waitingForTree?: boolean;
}

export interface DocTreeReceived extends Action<"DOC_TREE_RECEIVED"> {
  documentTree: DocRefTree;
}

export interface UpdateTreeAction {
  updatedTree: DocRefTree;
}
export interface DocRefsMoved
  extends Action<"DOC_REFS_MOVED">,
    UpdateTreeAction {
  docRefs: Array<DocRefType>;
  destination: DocRefType;
}
export interface DocRefsCopied
  extends Action<"DOC_REFS_COPIED">,
    UpdateTreeAction {
  docRefs: Array<DocRefType>;
  destination: DocRefType;
}
export interface DocRefsDeleted
  extends Action<"DOC_REFS_DELETED">,
    UpdateTreeAction {
  docRefs: Array<DocRefType>;
}
export interface DocRefCreated
  extends Action<"DOC_REF_CREATED">,
    UpdateTreeAction {}
export interface DocRefRenamed extends Action<"DOC_REF_RENAMED"> {
  docRef: DocRefType;
  name: string;
  resultDocRef: DocRefType;
}

export interface ActionCreators {
  docTreeReceived: ActionCreator<DocTreeReceived>;
  docRefsMoved: ActionCreator<DocRefsMoved>;
  docRefsCopied: ActionCreator<DocRefsCopied>;
  docRefsDeleted: ActionCreator<DocRefsDeleted>;
  docRefCreated: ActionCreator<DocRefCreated>;
  docRefRenamed: ActionCreator<DocRefRenamed>;
}

export const actionCreators: ActionCreators = {
  docTreeReceived: documentTree => ({ type: DOC_TREE_RECEIVED, documentTree }),
  docRefsMoved: (docRefs, destination, updatedTree) => ({
    type: DOC_REFS_MOVED,
    docRefs,
    destination,
    updatedTree
  }),
  docRefsCopied: (docRefs, destination, updatedTree) => ({
    type: DOC_REFS_COPIED,
    docRefs,
    destination,
    updatedTree
  }),
  docRefsDeleted: (docRefs, updatedTree) => ({
    type: DOC_REFS_DELETED,
    docRefs,
    updatedTree
  }),
  docRefCreated: updatedTree => ({
    type: DOC_REF_CREATED,
    updatedTree
  }),
  docRefRenamed: (docRef, name, resultDocRef) => ({
    type: DOC_REF_RENAMED,
    docRef,
    name,
    resultDocRef
  })
};

const defaultState: StoreState = {
  waitingForTree: true,
  uuid: "none",
  type: "System",
  name: "None"
};

export const reducer = prepareReducer(defaultState)
  .handleAction<DocTreeReceived>(
    DOC_TREE_RECEIVED,
    (state, { documentTree }) => documentTree
  )
  .handleAction<DocRefRenamed>(
    DOC_REF_RENAMED,
    (state, { docRef, resultDocRef }) =>
      updateItemInTree(state as DocRefTree, docRef.uuid, resultDocRef)
  )
  .handleActions<UpdateTreeAction>(
    [DOC_REFS_MOVED, DOC_REFS_COPIED, DOC_REFS_DELETED, DOC_REF_CREATED],
    (state, { updatedTree }) => updatedTree
  )
  .getReducer();
