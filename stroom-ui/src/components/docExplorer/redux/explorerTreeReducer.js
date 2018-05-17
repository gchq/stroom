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
import _ from 'lodash'
import { createAction, handleActions, combineActions } from 'redux-actions';

import {
    moveItemInTree, 
    iterateNodes, 
    getIsInFilteredMap,
    deleteItemFromTree
} from 'lib/treeUtils';

const OPEN_STATES = {
    closed: 0,
    byUser: 1,
    bySearch: 2
}

function isOpenState(openState) {
    if (!!openState) {
        return (openState !== OPEN_STATES.closed);
    } else {
        return false;
    }
}

function getToggledState(currentState, isUser) {
    if (!!currentState) {
        switch (currentState) {
            case OPEN_STATES.closed:
                return isUser ? OPEN_STATES.byUser : OPEN_STATES.bySearch;
            case OPEN_STATES.bySearch:
            case OPEN_STATES.byUser:
                return OPEN_STATES.closed;
        }
    } else {
        return OPEN_STATES.byUser;
    }
}

const DEFAULT_EXPLORER_ID = 'default';

const receiveDocTree = createAction('RECEIVE_DOC_TREE',
    documentTree => ({ documentTree }));
const explorerTreeOpened = createAction('EXPLORER_TREE_OPENED',
    (explorerId, allowMultiSelect, allowDragAndDrop, typeFilter) => ({explorerId, allowMultiSelect, allowDragAndDrop, typeFilter}));
const moveExplorerItem = createAction('MOVE_EXPLORER_ITEM',
    (explorerId, itemToMove, destination) => ({explorerId, itemToMove, destination}));
const toggleFolderOpen = createAction('TOGGLE_FOLDER_OPEN',
    (explorerId, docRef) => ({explorerId, docRef}))
const searchTermChanged = createAction('SEARCH_TERM_UPDATED',
    (explorerId, searchTerm) => ({explorerId, searchTerm}));
const selectDocRef = createAction('SELECT_DOC_REF',
    (explorerId, docRef) => ({explorerId, docRef}));
const openDocRef = createAction('OPEN_DOC_REF',
    (explorerId, docRef) => ({explorerId, docRef}))
const deleteDocRef = createAction('DELETE_DOC_REF',
    (explorerId, docRef) => ({explorerId, docRef}));
const openDocRefContextMenu = createAction('OPEN_DOC_REF_CONTEXT_MENU',
    (explorerId, docRef) => ({explorerId, docRef}));
const closeDocRefContextMenu = createAction('CLOSE_DOC_REF_CONTEXT_MENU',
    (explorerId) => ({explorerId}));

const defaultExplorerState = {
    searchTerm : '',
    isFolderOpen : {}, // in response to user actions and searches
    isSelected : {},
    isVisible : {}, // based on search
    inSearch: {},
    contextMenuItemUuid : undefined // will be a UUID
}

const defaultState = {
    documentTree : {}, // The hierarchy of doc refs in folders
    explorers : {},
    isDocRefOpen : {}, // in response to user actions
    allowMultiSelect : true,
    allowDragAndDrop : true
}

function getIsValidFilterTerm(filterTerm) {
    return (!!filterTerm && filterTerm.length > 0);
}

function getIsVisibleMap(documentTree, isInTypeFilterMap, isInSearchMap) {
    let isVisible = {};

    iterateNodes(documentTree, (lineage, node) => {
        let passesSearch = isInSearchMap[node.uuid];
        let passesTypeFilter = isInTypeFilterMap[node.uuid];
        isVisible[node.uuid] = passesSearch && passesTypeFilter;
    })

    return isVisible;
}

function getFolderIsOpenMap(documentTree, isInTypeFilterMap, isSearching, isInSearchMap, currentIsFolderOpen) {
    let isFolderOpen = {};

    iterateNodes(documentTree, (lineage, node) => {
        let s = currentIsFolderOpen[node.uuid]

        if (isSearching) {
            if (isInSearchMap[node.uuid] && !isOpenState(s)) {
                // If this node is not open, but is included in the search, open it
                isFolderOpen[node.uuid] = OPEN_STATES.bySearch;
            } else if (!isInSearchMap[node.uuid] && (s === OPEN_STATES.bySearch)) {
                // If this node was opened by search, but no longer matches the search...close it
                isFolderOpen[node.uuid] = OPEN_STATES.closed
            } else {
                // otherwise leave it as is
                isFolderOpen[node.uuid] = s
            }
        } else {
            if (s === OPEN_STATES.bySearch) {
                // If this node was opened by search, but we aren't searching any more, close it
                isFolderOpen[node.uuid] = OPEN_STATES.closed
            } else {
                // otherwise leave it as is
                isFolderOpen[node.uuid] = s
            }
        }
    })

    return isFolderOpen;
}

function getUpdatedExplorer(documentTree, optExplorer, searchTerm) {
    let explorer = (!!optExplorer) ? optExplorer : defaultExplorerState;

    let searchRegex;

    if (getIsValidFilterTerm(searchTerm)) {
        searchRegex = new RegExp(_.escapeRegExp(searchTerm), 'i')
    }

    let searchFilterFunction = (lineage, node) => {
        if (!!searchRegex) {
            return searchRegex.test(node.name);
        } else {
            return true;
        }
    }

    let typeFilterFunction = (lineage, node) => {
        if (getIsValidFilterTerm(explorer.typeFilter)) {
            return explorer.typeFilter === node.type;
        } else {
            return true;
        }
    }

    let isSearching = getIsValidFilterTerm(searchTerm);
    let isInSearchMap = getIsInFilteredMap(documentTree, searchFilterFunction);

    let isInTypeFilterMap = getIsInFilteredMap(documentTree, typeFilterFunction);
    return {
        ...explorer,
        searchTerm : searchTerm,
        isVisible : getIsVisibleMap(documentTree, isInTypeFilterMap, isInSearchMap),
        isFolderOpen : getFolderIsOpenMap(documentTree, isInTypeFilterMap, isSearching, isInSearchMap, explorer.isFolderOpen),
        inSearch : isInSearchMap
    }
}

function getStateAfterTreeUpdate(state, documentTree) {
    // Update all the explorers with the new tree
    let explorers = {};
    Object.entries(state.explorers).forEach(k => {
        explorers[k[0]] = getUpdatedExplorer(documentTree, k[1], k[1].searchTerm)
    });

    return {
        ...state,
        documentTree,
        explorers
    }
}

const explorerTreeReducer = handleActions(
    {
        // Receive the current state of the explorer tree
        [receiveDocTree] :
        (state, action) => {
            return getStateAfterTreeUpdate(state, action.payload.documentTree);
        },

        // When an explorer is opened
        [explorerTreeOpened]:
        (state, action) => {
            let { explorerId, allowMultiSelect, allowDragAndDrop, typeFilter } = action.payload;
            return {
                ...state,
                explorers: {
                    ...state.explorers,
                    [explorerId] : {
                        ...getUpdatedExplorer(state.documentTree, undefined, ''),
                        allowMultiSelect,
                        allowDragAndDrop,
                        typeFilter
                    }
                }
            }
        },

        // Move Item in Explorer Tree
        [moveExplorerItem]: 
        (state, action) => {
            let { itemToMove, destination } = action.payload;

            let documentTree = moveItemInTree(state.documentTree, itemToMove, destination);

            return getStateAfterTreeUpdate(state, documentTree);
        },

        // Folder Open Toggle
        [toggleFolderOpen]:
        (state, action) => {
            let { explorerId, docRef } = action.payload;

            return {
                ...state,
                explorers : {
                    ...state.explorers,
                    [explorerId] : {
                        ...state.explorers[explorerId],
                        isFolderOpen: {
                            ...state.explorers[explorerId].isFolderOpen,
                            [docRef.uuid] : getToggledState(state.explorers[explorerId].isFolderOpen[docRef.uuid], true)
                        }
                    }
                }   
            }
        },

        // Open Doc Ref Context Menu
        [openDocRefContextMenu]:
        (state, action) => {
            let { explorerId, docRef } = action.payload;

            return {
                ...state,
                explorers : {
                    ...state.explorers,
                    [explorerId]: {
                        ...state.explorers[explorerId],
                        contextMenuItemUuid : docRef.uuid
                    }
                }
            }
        },

        // Close Doc Ref Context Menu
        [closeDocRefContextMenu]:
        (state, action) => {
            let { explorerId, docRef } = action.payload;

            return {
                ...state,
                explorers : {
                    ...state.explorers,
                    [explorerId]: {
                        ...state.explorers[explorerId],
                        contextMenuItemUuid : undefined
                    }
                }
            }
        },

        // Search Term Changed
        [searchTermChanged]:
        (state, action) => {
            let { explorerId, searchTerm } = action.payload;

            let explorer = getUpdatedExplorer(state.documentTree, state.explorers[explorerId], searchTerm);

            return {
                ...state,
                explorers : {
                    ...state.explorers,
                    [explorerId] : explorer
                }
            }
        },

        // Select Doc Ref
        [selectDocRef]:
        (state, action) => {
            let { explorerId, docRef } = action.payload;

            let explorer = state.explorers[explorerId];
            let isSelected;
            if (explorer.allowMultiSelect) {
                isSelected = {
                    ...state.explorers[explorerId].isSelected,
                    [docRef.uuid] : !state.explorers[explorerId].isSelected[docRef.uuid]
                }
            } else {
                isSelected = {
                    [docRef.uuid] : !state.explorers[explorerId].isSelected[docRef.uuid]
                }
            }

            return {
                ...state,
                explorers : {
                    ...state.explorers,
                    [explorerId] : {
                        ...state.explorers[explorerId],
                        isSelected
                    }
                }
            }
        },

        // Open Doc Ref
        [openDocRef]:
        (state, action) => {
            let { explorerId, docRef } = action.payload;

            return {
                ...state,
                isDocRefOpen: {
                    ...state.isDocRefOpen,
                    [docRef.uuid] : !state.isDocRefOpen[docRef.uuid]
                }
            }
        },

        // Delete Doc Ref
        [deleteDocRef]:
        (state, action) => {
            let { explorerId, docRef } = action.payload;

            let documentTree = deleteItemFromTree(state.documentTree, docRef.uuid);

            return getStateAfterTreeUpdate(state, documentTree);
        }
    },
    defaultState
)

export {
    DEFAULT_EXPLORER_ID,
    receiveDocTree,
    explorerTreeOpened,
    moveExplorerItem,
    toggleFolderOpen,
    openDocRef,
    deleteDocRef,
    searchTermChanged,
    selectDocRef,
    openDocRefContextMenu,
    closeDocRefContextMenu,
    explorerTreeReducer
}