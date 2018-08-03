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

import {
  moveItemsInTree,
  copyItemsInTree,
  iterateNodes,
  getIsInFilteredMap,
  deleteItemsFromTree,
  addItemToTree,
  updateItemInTree,
} from 'lib/treeUtils';

export const DEFAULT_EXPLORER_ID = 'default';

export const actionCreators = createActions({
  DOC_TREE_RECEIVED: documentTree => ({ documentTree }),
  DOC_EXPLORER_OPENED: (explorerId, allowMultiSelect, typeFilters) => ({
    explorerId,
    allowMultiSelect,
    typeFilters,
  }),
  FOLDER_OPEN_TOGGLED: (explorerId, docRef) => ({
    explorerId,
    docRef,
  }),
  DOC_REF_SELECTED: (explorerId, docRef, appendSelection, contiguousSelection) => ({
    explorerId,
    docRef,
    appendSelection,
    contiguousSelection,
  }),
  DOC_REF_CONTEXT_MENU_OPENED: (explorerId, docRef) => ({
    explorerId,
    docRef,
  }),
  DOC_REF_CONTEXT_MENU_CLOSED: explorerId => ({
    explorerId,
  }),
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

const defaultExplorerState = {
  isFolderOpen: {}, // in response to user actions
  inTypeFilter: {},
};

const defaultState = {
  documentTree: undefined, // The hierarchy of doc refs in folders
  explorers: {},
  isTreeReady: false,
  isDocRefTypeListReady: false,
};

function getIsValidFilterTerm(filterTerm) {
  return !!filterTerm && filterTerm.length > 1; // at least 2 characters
}

function getUpdatedExplorer(documentTree, explorer) {
  const typeFilterFunc = (l, n) =>
    !explorer.typeFilters || explorer.typeFilters.length === 0 || explorer.typeFilters.includes(n.type);
  const inTypeFilter = getIsInFilteredMap(documentTree, typeFilterFunc);

  return {
    ...explorer,
    inTypeFilter,
  };
}

function getStateAfterTreeUpdate(state, documentTree) {
  // Update all the explorers with the new tree
  const explorers = {};
  Object.entries(state.explorers).forEach((k) => {
    explorers[k[0]] = getUpdatedExplorer(documentTree, k[1]);
  });

  return {
    ...state,
    documentTree,
    explorers,
    isTreeReady: true,
  };
}

const { docRefContextMenuOpened, docRefContextMenuClosed } = actionCreators;

const PROCESS_PHASE = {
  LOOKING_FOR_START: 0,
  ADDING: 1,
  FINISHED: 2,
};

export const reducer = handleActions(
  {
    // Receive the current state of the explorer tree
    DOC_TREE_RECEIVED: (state, action) =>
      getStateAfterTreeUpdate(state, action.payload.documentTree),

    // When an explorer is opened (if this ID has been opened before, it will use that old state)
    DOC_EXPLORER_OPENED: (state, action) => {
      const { explorerId, allowMultiSelect, typeFilters } = action.payload;
      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: state.explorers[explorerId] || {
            ...getUpdatedExplorer(
              state.documentTree,
              {
                ...defaultExplorerState,
                isSelected: allowMultiSelect ? {} : undefined,
                isSelectedList: allowMultiSelect ? [] : undefined,
                typeFilters,
              },
            ),
            allowMultiSelect,
          },
        },
      };
    },

    // Folder Open Toggle
    FOLDER_OPEN_TOGGLED: (state, action) => {
      const { explorerId, docRef } = action.payload;
      const explorer = state.explorers[explorerId];

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: {
            ...explorer,
            isFolderOpen: {
              ...explorer.isFolderOpen,
              [docRef.uuid]: !explorer.isFolderOpen[docRef.uuid],
            },
          },
        },
      };
    },

    // Select Doc Ref
    DOC_REF_SELECTED: (state, action) => {
      const {
        explorerId, docRef, appendSelection, contiguousSelection,
      } = action.payload;
      const explorer = state.explorers[explorerId];

      // Only allow selection to be made if the doc ref is within the type filters
      // This prevents selection of folders when folders are only visible to allow underlying docs to be shown
      if (explorer.typeFilters.length > 0 && !explorer.typeFilters.includes(docRef.type)) {
        return state;
      }

      let isSelected;
      let isSelectedList;
      if (explorer.allowMultiSelect) {
        if (appendSelection) {
          isSelected = {
            ...explorer.isSelected,
            [docRef.uuid]: !explorer.isSelected[docRef.uuid],
          };
        } else {
          isSelected = {
            [docRef.uuid]: !explorer.isSelected[docRef.uuid],
          };
        }

        // Selecting contiguous files is somewhat complex!
        if (contiguousSelection && explorer.lastSelectedUuid) {
          // We will iterate through the nodes looking for one of the selection endpoints.
          let phase = PROCESS_PHASE.LOOKING_FOR_START;
          iterateNodes(state.documentTree, (lineage, node) => {
            // The selection should be inclusive of start and end
            let addThisOne = phase === PROCESS_PHASE.ADDING;

            // If we have hit one of the endpoints, move the phase along
            if (node.uuid === explorer.lastSelectedUuid || node.uuid === docRef.uuid) {
              switch (phase) {
                case PROCESS_PHASE.LOOKING_FOR_START:
                  phase = PROCESS_PHASE.ADDING;
                  addThisOne = true;
                  break;
                case PROCESS_PHASE.ADDING:
                  phase = PROCESS_PHASE.FINISHED;
                  break;
                default:
                  break;
              }
            }

            if (addThisOne) {
              isSelected[node.uuid] = explorer.inTypeFilter[node.uuid];
            }

            // Skip children if the folder is NOT open
            return !explorer.isFolderOpen[node.uuid];
          });
        }

        isSelectedList = Object.entries(isSelected)
          .filter(k => k[1])
          .map(k => k[0]);
      } else if (!explorer.isSelected || explorer.isSelected !== docRef.uuid) {
        isSelected = docRef.uuid;
      }

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: {
            ...state.explorers[explorerId],
            isSelected,
            isSelectedList,
            lastSelectedUuid: docRef.uuid,
          },
        },
      };
    },

    // Open or Close a DocRef/Folder Context Menu
    [combineActions(docRefContextMenuOpened, docRefContextMenuClosed)]: (state, action) => {
      const { explorerId, docRef } = action.payload;

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: {
            ...state.explorers[explorerId],
            contentMenuDocRef: docRef,
          },
        },
      };
    },

    // Confirm Delete Doc Ref
    DOC_REFS_DELETED: (state, action) => {
      const { bulkActionResult } = action.payload;

      const documentTree = deleteItemsFromTree(
        state.documentTree,
        bulkActionResult.docRefs.map(d => d.uuid),
      );

      return getStateAfterTreeUpdate(state, documentTree);
    },

    DOC_REF_CREATED: (state, action) => {
      const { docRef, parentFolder } = action.payload;

      const documentTree = addItemToTree(state.documentTree, parentFolder.uuid, docRef);

      return getStateAfterTreeUpdate(state, documentTree);
    },

    DOC_REF_RENAMED: (state, action) => {
      const { docRef, resultDocRef } = action.payload;

      const documentTree = updateItemInTree(state.documentTree, docRef.uuid, resultDocRef);
      return getStateAfterTreeUpdate(state, documentTree);
    },

    DOC_REFS_COPIED: (state, action) => {
      const {
        destination,
        bulkActionResult: { docRefs },
      } = action.payload;

      const documentTree = copyItemsInTree(state.documentTree, docRefs, destination);

      return getStateAfterTreeUpdate(state, documentTree);
    },

    DOC_REFS_MOVED: (state, action) => {
      const {
        destination,
        bulkActionResult: { docRefs },
      } = action.payload;

      const documentTree = moveItemsInTree(state.documentTree, docRefs, destination);

      return getStateAfterTreeUpdate(state, documentTree);
    },
  },
  defaultState,
);
