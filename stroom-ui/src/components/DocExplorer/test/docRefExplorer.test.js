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
import { createStore } from 'redux';

import { guid, iterateNodes } from 'lib/treeUtils';
import { combineReducers } from 'redux';

import {
  actionCreators as docExplorerActionCreators,
  reducer as docExplorerReducer,
} from '../redux';
import {
  actionCreators as docRefTypesActionCreators,
  reducer as docRefTypesReducer,
} from '../../DocRefTypes/redux';

import { testTree } from './index';
import { testDocRefsTypes } from '../../DocRefTypes/test';

const {
  docTreeReceived,
  docExplorerOpened,
  folderOpenToggled,
  tabOpened,
  docRefSelected,
  closeContextMenu,
} = docExplorerActionCreators;

const { docRefTypesReceived } = docRefTypesActionCreators;

// Rebuilt for each test
let store;
const reducer = combineReducers({
  docExplorer: docExplorerReducer,
  docRefTypes: docRefTypesReducer,
});

describe('Doc Explorer Reducer', () => {
  beforeEach(() => {
    store = createStore(reducer);
    store.dispatch(docRefTypesReceived(testDocRefsTypes));
    store.dispatch(docTreeReceived(testTree));
  });

  describe('Explorer Tree', () => {
    it('should contain the test tree', () => {
      const state = store.getState();
      expect(state).toHaveProperty('docExplorer');
      expect(state.docExplorer).toHaveProperty('explorerTree');
      expect(state.docExplorer.explorerTree).toHaveProperty('documentTree');
      expect(state.docExplorer.explorerTree.documentTree).toBe(testTree);
    });
    it('should create a new explorer state for given ID', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const typeFilters = [];

      // When
      store.dispatch(docExplorerOpened(explorerId, allowMultiSelect, typeFilters));

      // Then
      const {
        docExplorer: {explorers },
      } = store.getState();
      expect(explorers).toHaveProperty(explorerId);
      const explorer = explorers[explorerId];

      expect(explorer.typeFilters).toEqual([]);
      expect(explorer.allowMultiSelect).toBe(true);
    });
  });

  describe('Type Filtering', () => {
    it('should make all doc refs visible with no type filter', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      let typeFilters;

      // When
      store.dispatch(docExplorerOpened(explorerId, allowMultiSelect, typeFilters));
      const {
        docExplorer: {
          explorerTree: { documentTree, explorers },
        },
      } = store.getState();
      const explorer = explorers[explorerId];

      // Then
      iterateNodes(documentTree, (lineage, node) => {
        expect(explorer.inTypeFilter[node.uuid]).toBe(true);
      });
    });
    it('should only make doc refs matching type filter visible', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const typeFilters = ['XSLT'];

      // When
      store.dispatch(docExplorerOpened(explorerId, allowMultiSelect, typeFilters));

      // Then
      const {
        docExplorer: {
          explorerTree: { explorers },
        },
      } = store.getState();
      expect(explorers).toHaveProperty(explorerId);
      const explorer = explorers[explorerId];

      // Check a folder that contains a match
      expect(explorer.inTypeFilter[testTree.children[0].uuid]).toBe(true);

      // Check a matching doc
      expect(explorer.inTypeFilter[testTree.children[0].children[0].children[2].uuid]).toBe(true);

      // Check a folder that doesn't contain a matching
      expect(explorer.inTypeFilter[testTree.children[1].children[1].uuid]).toBe(false);

      // Check a non matching doc
      expect(explorer.inTypeFilter[testTree.children[0].children[0].children[1].uuid]).toBe(false);
    });
    it('should only make folders visible', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const typeFilters = ['Folder'];

      // When
      store.dispatch(docExplorerOpened(explorerId, allowMultiSelect, typeFilters));

      // Then
      const {
        docExplorer: {
          explorerTree: { explorers },
        },
      } = store.getState();
      expect(explorers).toHaveProperty(explorerId);
      const explorer = explorers[explorerId];

      // Check a folder
      expect(explorer.inTypeFilter[testTree.children[1].children[1].uuid]).toBe(true);

      // Check a doc
      expect(explorer.inTypeFilter[testTree.children[0].children[0].children[2].uuid]).toBe(false);
    });
  });
});
