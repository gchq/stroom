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
import { createActions, handleActions, combineActions } from 'redux-actions';

import { updateItemInTree } from 'lib/treeUtils';

export const DEFAULT_EXPLORER_ID = 'default';

export const actionCreators = createActions({
  DOC_TREE_RECEIVED: documentTree => ({ documentTree }),
  DOC_REFS_MOVED: (docRefs, destination, updatedTree) => ({
    docRefs,
    destination,
    updatedTree,
  }),
  DOC_REFS_COPIED: (docRefs, destination, updatedTree) => ({
    docRefs,
    destination,
    updatedTree,
  }),
  DOC_REFS_DELETED: (docRefs, updatedTree) => ({
    docRefs,
    updatedTree,
  }),
  DOC_REF_CREATED: (updatedTree) => ({
    updatedTree,
  }),
  DOC_REF_RENAMED: (docRef, name, resultDocRef) => ({
    docRef,
    name,
    resultDocRef,
  }),
});

const {
  docRefsMoved, docRefsCopied, docRefsDeleted, docRefCreated,
} = actionCreators;

const defaultState = {
  waitingForTree: true,
  uuid: 'none',
  type: 'System',
  name: 'None',
};

export const reducer = handleActions(
  {
    // Receive the current state of the explorer tree
    DOC_TREE_RECEIVED: (state, { payload: { documentTree } }) => documentTree,

    DOC_REF_RENAMED: (state, action) => {
      const { docRef, resultDocRef } = action.payload;

      const documentTree = updateItemInTree(state, docRef.uuid, resultDocRef);
      return documentTree;
    },

    [combineActions(docRefsMoved, docRefsCopied, docRefsDeleted, docRefCreated)]: (
      state,
      { payload: { updatedTree } },
    ) => updatedTree,
  },
  defaultState,
);
