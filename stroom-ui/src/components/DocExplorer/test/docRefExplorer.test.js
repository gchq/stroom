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

const { docTreeReceived } = docExplorerActionCreators;

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
      expect(state.docExplorer).toHaveProperty('documentTree');
      expect(state.docExplorer.documentTree).toBe(testTree);
    });
  });
});
