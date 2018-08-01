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
import * as JsSearch from 'js-search';

import {
  moveItemsInTree,
  copyItemsInTree,
  iterateNodes,
  getIsInFilteredMap,
  deleteItemsFromTree,
  addItemToTree,
  updateItemInTree,
} from 'lib/treeUtils';

const OPEN_STATES = {
  closed: 0,
  byUser: 1,
  bySearch: 2,
};

function isOpenState(openState) {
  if (openState) {
    return openState !== OPEN_STATES.closed;
  }
  return false;
}

function getToggledState(currentState, isUser) {
  if (currentState) {
    switch (currentState) {
      case OPEN_STATES.closed:
        return isUser ? OPEN_STATES.byUser : OPEN_STATES.bySearch;
      case OPEN_STATES.bySearch:
      case OPEN_STATES.byUser:
        return OPEN_STATES.closed;
      default:
        throw new Error(`Invalid current state: ${currentState}`);
    }
  } else {
    return OPEN_STATES.byUser;
  }
}

export const DEFAULT_EXPLORER_ID = 'default';

export const actionCreators = createActions({
  DOC_REF_TYPES_RECEIVED: docRefTypes => ({ docRefTypes }),
  DOC_TREE_RECEIVED: documentTree => ({ documentTree }),
  DOC_EXPLORER_OPENED: (explorerId, allowMultiSelect, typeFilters) => ({
    explorerId,
    allowMultiSelect,
    typeFilters,
  }),
  TYPE_FILTER_CHANGED: (explorerId, docRefType, newValue) => ({
    explorerId,
    docRefType,
    newValue,
  }),
  FOLDER_OPEN_TOGGLED: (explorerId, docRef) => ({
    explorerId,
    docRef,
  }),
  SEARCH_TERM_UPDATED: (explorerId, searchTerm) => ({
    explorerId,
    searchTerm,
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
  searchTerm: '',
  searchExecutor: undefined,
  searchResults: [],
  isFolderOpen: {}, // in response to user actions and searches
  isVisible: {}, // based on search
  inSearch: {},
  inTypeFilter: {},
};

const defaultState = {
  documentTree: undefined, // The hierarchy of doc refs in folders
  explorers: {},
  allowMultiSelect: true,
  isTreeReady: false,
  isDocRefTypeListReady: false,
  docRefTypes: [],
};

function getIsValidFilterTerm(filterTerm) {
  return !!filterTerm && filterTerm.length > 1; // at least 2 characters
}

function getIsVisibleMap(documentTree, searchFilterFunc, typeFilterFunc) {
  const isVisible = {};

  iterateNodes(documentTree, (lineage, node) => {
    const passesSearch = searchFilterFunc(lineage, node);
    const passesTypeFilter = typeFilterFunc(lineage, node);

    const nodeIsVisible = passesSearch && passesTypeFilter;
    if (nodeIsVisible) {
      lineage.forEach(n => (isVisible[n.uuid] = true));
    }

    isVisible[node.uuid] = nodeIsVisible;
  });

  return isVisible;
}

function getFolderIsOpenMap(
  documentTree,
  isInTypeFilterMap,
  isSearching,
  isInSearchMap,
  currentIsFolderOpen,
  isNewExplorer,
) {
  const isFolderOpen = {};
  let isFirst = true;

  iterateNodes(documentTree, (lineage, node) => {
    const s = currentIsFolderOpen[node.uuid];

    if (!node.children || node.children.length === 0) {
      isFolderOpen[node.uuid] = OPEN_STATES.closed;
    } else if (isFirst && isNewExplorer) {
      isFolderOpen[node.uuid] = OPEN_STATES.byUser;
      isFirst = false;
    } else if (isSearching) {
      if (isInSearchMap[node.uuid] && !isOpenState(s)) {
        // If this node is not open, but is included in the search, open it
        isFolderOpen[node.uuid] = OPEN_STATES.bySearch;
      } else if (!isInSearchMap[node.uuid] && s === OPEN_STATES.bySearch) {
        // If this node was opened by search, but no longer matches the search...close it
        isFolderOpen[node.uuid] = OPEN_STATES.closed;
      } else {
        // otherwise leave it as is
        isFolderOpen[node.uuid] = s;
      }
    } else if (s === OPEN_STATES.bySearch) {
      // If this node was opened by search, but we aren't searching any more, close it
      isFolderOpen[node.uuid] = OPEN_STATES.closed;
    } else {
      // otherwise leave it as is
      isFolderOpen[node.uuid] = s;
    }
  });

  return isFolderOpen;
}

function getUpdatedExplorer({
  documentTree,
  explorer,
  searchExecutor,
  searchTerm,
  typeFilters,
  isNewExplorer,
}) {
  let searchResults;
  if (searchExecutor && searchTerm) {
    searchResults = searchExecutor
      .search(searchTerm)
      .reduce((acc, curr) => ({ ...acc, [curr.uuid]: true }), {});
  }

  const isSearching = getIsValidFilterTerm(searchTerm);
  const searchFilterFunc = (l, n) => !isSearching || !!searchResults[n.uuid];
  const isInSearchMap = getIsInFilteredMap(documentTree, searchFilterFunc);
  const typeFilterFunc = (l, n) => typeFilters.length === 0 || typeFilters.includes(n.type);
  const isInTypeFilterMap = getIsInFilteredMap(documentTree, typeFilterFunc);

  // Derive the combined mapping of visibility
  const isVisible = getIsVisibleMap(documentTree, searchFilterFunc, typeFilterFunc);

  return {
    ...explorer,
    typeFilters,
    searchTerm,
    isVisible,
    isFolderOpen: getFolderIsOpenMap(
      documentTree,
      isInTypeFilterMap,
      isSearching,
      isInSearchMap,
      explorer.isFolderOpen,
      isNewExplorer,
    ),
    inSearch: isInSearchMap,
    inTypeFilter: isInTypeFilterMap,
  };
}

function getStateAfterTreeUpdate(state, documentTree, isNewTree) {
  // Create the search index
  const rawSearchData = [];
  iterateNodes(documentTree, (lineage, node) => {
    rawSearchData.push({
      name: node.name,
      type: node.type,
      uuid: node.uuid,
      lineage,
      lineageNames: lineage.reduce((acc, curr) => `${acc} ${curr.name}`, ''),
    });
  });

  const searchExecutor = new JsSearch.Search('uuid');
  searchExecutor.addIndex('name');
  searchExecutor.addIndex('lineageNames');
  searchExecutor.addDocuments(rawSearchData);

  // Update all the explorers with the new tree
  const explorers = {};
  Object.entries(state.explorers).forEach((k) => {
    explorers[k[0]] = getUpdatedExplorer({
      documentTree,
      explorer: k[1],
      searchExecutor,
      searchTerm: k[1].searchTerm,
      typeFilters: k[1].typeFilters,
      isNewExplorer: isNewTree,
    });
  });

  return {
    ...state,
    documentTree,
    explorers,
    isTreeReady: true,
    searchExecutor,
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
    // Receive the set of doc ref types used in the current tree
    DOC_REF_TYPES_RECEIVED: (state, { payload: { docRefTypes } }) => {
      const explorers = {};
      Object.entries(state.explorers)
        .map(k => ({ explorerId: k[0], explorer: k[1] }))
        .forEach(({ explorerId, explorer }) =>
          (explorers[explorerId] = getUpdatedExplorer({
            documentTree: state.documentTree,
            explorer,
            searchExecutor: state.searchExecutor,
            searchTerm: explorer.searchTerm,
            typeFilters: explorer.typeFilters.length === 0 ? docRefTypes : explorer.typeFilters,
          })));

      return {
        ...state,
        docRefTypes,
        isDocRefTypeListReady: true,
        explorers,
      };
    },

    // Receive the current state of the explorer tree
    DOC_TREE_RECEIVED: (state, action) =>
      getStateAfterTreeUpdate(state, action.payload.documentTree, true),

    // When an explorer is opened (if this ID has been opened before, it will use that old state)
    DOC_EXPLORER_OPENED: (state, action) => {
      const { explorerId, allowMultiSelect, typeFilters } = action.payload;
      const typeFiltersToUse =
        typeFilters && typeFilters.length > 0 ? typeFilters : state.docRefTypes;
      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: state.explorers[explorerId] || {
            ...getUpdatedExplorer({
              documentTree: state.documentTree,
              explorer: {
                ...defaultExplorerState,
                isSelected : allowMultiSelect ? {} : undefined,
                isSelectedList : allowMultiSelect ? [] : undefined,
              },
              searchExecutor: state.searchExecutor,
              searchTerm: '',
              typeFilters: typeFiltersToUse,
              isNewExplorer: true,
            }),
            allowMultiSelect,
          },
        },
      };
    },

    // Type Filter changed
    TYPE_FILTER_CHANGED: (state, action) => {
      const { explorerId, docRefType, newValue } = action.payload;

      const currentExplorer = state.explorers[explorerId];
      let updatedFilters = currentExplorer.typeFilters.filter(t => t !== docRefType);
      if (newValue) {
        updatedFilters = updatedFilters.concat([docRefType]);
      }

      const updatedExplorer = getUpdatedExplorer({
        documentTree: state.documentTree,
        explorer: currentExplorer,
        searchExecutor: state.searchExecutor,
        searchTerm: currentExplorer.searchTerm,
        typeFilters: updatedFilters,
      });

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: updatedExplorer,
        },
      };
    },

    // Folder Open Toggle
    FOLDER_OPEN_TOGGLED: (state, action) => {
      const { explorerId, docRef } = action.payload;

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: {
            ...state.explorers[explorerId],
            isFolderOpen: {
              ...state.explorers[explorerId].isFolderOpen,
              [docRef.uuid]: getToggledState(
                state.explorers[explorerId].isFolderOpen[docRef.uuid],
                true,
              ),
            },
          },
        },
      };
    },

    // Search Term Changed
    SEARCH_TERM_UPDATED: (state, action) => {
      const { explorerId, searchTerm } = action.payload;

      const explorer = getUpdatedExplorer({
        documentTree: state.documentTree,
        explorer: state.explorers[explorerId],
        searchExecutor: state.searchExecutor,
        searchTerm,
        typeFilters: state.explorers[explorerId].typeFilters,
      });

      return {
        ...state,
        explorers: {
          ...state.explorers,
          [explorerId]: explorer,
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
      if (!explorer.typeFilters.includes(docRef.type)) {
        return state;
      }

      let isSelected;
      let isSelectedList;
      if (explorer.allowMultiSelect) {
        if (appendSelection) {
          isSelected = {
            ...state.explorers[explorerId].isSelected,
            [docRef.uuid]: !state.explorers[explorerId].isSelected[docRef.uuid],
          };
        } else {
          isSelected = {
            [docRef.uuid]: !state.explorers[explorerId].isSelected[docRef.uuid],
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
              isSelected[node.uuid] = explorer.isVisible[node.uuid];
            }

            // Skip children if the folder is NOT open
            return !explorer.isFolderOpen[node.uuid];
          });
        }

        isSelectedList = Object.entries(isSelected)
          .filter(k => k[1])
          .map(k => k[0]);
      } else {
        if (!explorer.isSelected || explorer.isSelected !== docRef.uuid) {
          isSelected = docRef.uuid;
        }
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

      return getStateAfterTreeUpdate(state, documentTree, false);
    },

    DOC_REF_CREATED: (state, action) => {
      const { docRef, parentFolder } = action.payload;

      const documentTree = addItemToTree(state.documentTree, parentFolder.uuid, docRef);

      return getStateAfterTreeUpdate(state, documentTree, false);
    },

    DOC_REF_RENAMED: (state, action) => {
      const { docRef, resultDocRef } = action.payload;

      const documentTree = updateItemInTree(state.documentTree, docRef.uuid, resultDocRef);
      return getStateAfterTreeUpdate(state, documentTree, false);
    },

    DOC_REFS_COPIED: (state, action) => {
      const {
        destination,
        bulkActionResult: { docRefs },
      } = action.payload;

      const documentTree = copyItemsInTree(state.documentTree, docRefs, destination);

      return getStateAfterTreeUpdate(state, documentTree, false);
    },

    DOC_REFS_MOVED: (state, action) => {
      const {
        destination,
        bulkActionResult: { docRefs },
      } = action.payload;

      const documentTree = moveItemsInTree(state.documentTree, docRefs, destination);

      return getStateAfterTreeUpdate(state, documentTree, false);
    },
  },
  defaultState,
);
