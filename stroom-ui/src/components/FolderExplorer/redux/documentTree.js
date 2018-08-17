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
import { createActions, handleActions } from 'redux-actions';

import { deleteItemsFromTree, addItemsToTree, updateItemInTree } from 'lib/treeUtils';

export const DEFAULT_EXPLORER_ID = 'default';

export const actionCreators = createActions({
  DOC_TREE_RECEIVED: documentTree => ({ documentTree }),
  DOC_REFS_MOVED: (docRefs, destination, bulkActionResult) => ({
    docRefs,
    destination,
    bulkActionResult,
  }),
  DOC_REFS_COPIED: (docRefs, destination, bulkActionResult) => ({
    docRefs,
    destination,
    bulkActionResult,
  }),
  DOC_REFS_DELETED: (docRefs, bulkActionResult) => ({
    docRefs,
    bulkActionResult,
  }),
  DOC_REF_CREATED: (docRef, parentFolder) => ({
    docRef,
    parentFolder,
  }),
  DOC_REF_RENAMED: (docRef, name, resultDocRef) => ({
    docRef,
    name,
    resultDocRef,
  }),
});

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

    // Confirm Delete Doc Ref
    DOC_REFS_DELETED: (state, action) => {
      const { bulkActionResult } = action.payload;

      const documentTree = deleteItemsFromTree(state, bulkActionResult.docRefs.map(d => d.uuid));

      return documentTree;
    },

    DOC_REF_CREATED: (state, action) => {
      const { docRef, parentFolder } = action.payload;

      const documentTree = addItemsToTree(state, parentFolder.uuid, [docRef]);

      return documentTree;
    },

    DOC_REF_RENAMED: (state, action) => {
      const { docRef, resultDocRef } = action.payload;

      const documentTree = updateItemInTree(state, docRef.uuid, resultDocRef);
      return documentTree;
    },

    DOC_REFS_COPIED: (state, action) => {
      const {
        destination,
        bulkActionResult: { docRefs },
      } = action.payload;

      const documentTree = addItemsToTree(state, destination.uuid, docRefs);

      return documentTree;
    },

    DOC_REFS_MOVED: (state, action) => {
      const {
        destination,
        bulkActionResult: { docRefs },
      } = action.payload;

      let documentTree = deleteItemsFromTree(state, docRefs.map(d => d.uuid));
      documentTree = addItemsToTree(documentTree, destination.uuid, deleteItemsFromTree);

      return documentTree;
    },
  },
  defaultState,
);
