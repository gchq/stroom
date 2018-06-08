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

import { actionCreators } from './redux';

import { testTree, DOC_REF_TYPES } from './documentTree.testData';

import { explorerTreeReducer, DEFAULT_EXPLORER_ID } from './redux';

const {
  receiveDocTree,
  explorerTreeOpened,
  moveExplorerItem,
  toggleFolderOpen,
  openDocRef,
  searchTermUpdated,
  selectDocRef,
  openDocRefContextMenu,
  closeDocRefContextMenu,
} = actionCreators;

// Rebuilt for each test
let store;

describe('Doc Explorer Reducer', () => {
  beforeEach(() => {
    store = createStore(explorerTreeReducer);
  });

  describe('Explorer Tree', () => {
    it('should contain the test tree', () => {
      store.dispatch(receiveDocTree(testTree));

      const state = store.getState();
      expect(state).to.have.property('documentTree');
      expect(state.documentTree).to.be(testTree);
    });
    it('should create a new explorer state for given ID', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const allowDragAndDrop = true;
      let typeFilter;

      // When
      store.dispatch(receiveDocTree(testTree));
      store.dispatch(explorerTreeOpened(explorerId, allowMultiSelect, allowDragAndDrop, typeFilter));

      // Then
      const state = store.getState();
      expect(state.explorers).to.have.property(explorerId);
      const explorer = state.explorers[explorerId];
      expect(explorer.typeFilter).to.be(undefined);
      expect(explorer.allowMultiSelect).to.be(true);
      expect(explorer.allowDragAndDrop).to.be(true);
    });
  });

  describe('Type Filtering', () => {
    it('should make all doc refs visible with no type filter', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const allowDragAndDrop = true;
      let typeFilter;

      // When
      store.dispatch(receiveDocTree(testTree));
      store.dispatch(explorerTreeOpened(explorerId, allowMultiSelect, allowDragAndDrop, typeFilter));
      const state = store.getState();
      const explorer = state.explorers[explorerId];

      // Then
      iterateNodes(state.documentTree, (lineage, node) => {
        expect(explorer.isVisible[node.uuid]).to.be(true);
      });
    });
    it('should only make doc refs matching type filter visible', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const allowDragAndDrop = true;
      const typeFilter = DOC_REF_TYPES.XSLT;

      // When
      store.dispatch(receiveDocTree(testTree));
      store.dispatch(explorerTreeOpened(explorerId, allowMultiSelect, allowDragAndDrop, typeFilter));

      // Then
      const state = store.getState();
      expect(state.explorers).to.have.property(explorerId);
      const explorer = state.explorers[explorerId];

      // Check a folder that contains a match
      expect(explorer.isVisible[testTree.children[0].uuid]).to.be(true);

      // Check a matching doc
      expect(explorer.isVisible[testTree.children[0].children[0].children[2].uuid]).to.be(true);

      // Check a folder that doesn't contain a matching
      expect(explorer.isVisible[testTree.children[1].children[1].uuid]).to.be(false);

      // Check a non matching doc
      expect(explorer.isVisible[testTree.children[0].children[0].children[1].uuid]).to.be(false);
    });
    it('should combine search terms and type filters correctly', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const allowDragAndDrop = true;
      const typeFilter = DOC_REF_TYPES.DICTIONARY;
      const searchTerm = testTree.children[0].children[1].children[0].name;

      // When
      store.dispatch(receiveDocTree(testTree));
      store.dispatch(explorerTreeOpened(explorerId, allowMultiSelect, allowDragAndDrop, typeFilter));
      store.dispatch(searchTermUpdated(explorerId, searchTerm));
      store.dispatch(searchTermUpdated(explorerId, undefined));

      // Then
      const state = store.getState();
      expect(state.explorers).to.have.property(explorerId);
      const explorer = state.explorers[explorerId];

      // Check a matching folder
      expect(explorer.isVisible[testTree.children[0].children[1].uuid]).to.be(true);

      // Check a matching doc
      expect(explorer.isVisible[testTree.children[0].children[1].children[0].uuid]).to.be(true);

      // Check a folder that doesn't contain a matching
      expect(explorer.isVisible[testTree.children[2].children[0].uuid]).to.be(false);

      // Check a non matching doc
      expect(explorer.isVisible[testTree.children[3].uuid]).to.be(false);
    });
    it('should combine search terms and type filters correctly', () => {
      // Given
      const explorerId = guid();
      const allowMultiSelect = true;
      const allowDragAndDrop = true;
      const typeFilter = DOC_REF_TYPES.Index;
      const subTree = testTree.children[1];
      const searchTerm = subTree.children[0].children[3].name;

      // When
      store.dispatch(receiveDocTree(subTree));
      store.dispatch(explorerTreeOpened(explorerId, allowMultiSelect, allowDragAndDrop, typeFilter));
      store.dispatch(searchTermUpdated(explorerId, searchTerm));
      store.dispatch(searchTermUpdated(explorerId, undefined));

      // Then
      const state = store.getState();
      expect(state.explorers).to.have.property(explorerId);
      const explorer = state.explorers[explorerId];

      // Check the matching doc
      expect(explorer.isVisible[subTree.children[0].children[3].uuid]).to.be(true);

      // Check not matching docs
      expect(explorer.isVisible[subTree.children[0].children[5].uuid]).to.be(false);

      // Check matching folder
      expect(explorer.isVisible[subTree.children[0].uuid]).to.be(true);
    });
  });
});
