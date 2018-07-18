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

import { iterateNodes } from 'lib/treeUtils';
import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';

const actionCreators = createActions({
  APP_SEARCH_TERM_UPDATED: searchTerm => ({ searchTerm }),
  APP_SEARCH_OPENED: () => ({ isOpen: true }),
  APP_SEARCH_CLOSED: () => ({ isOpen: false }),
  APP_SEARCH_SELECTION_UP: () => ({}),
  APP_SEARCH_SELECTION_DOWN: () => ({}),
});

const { appSearchOpened, appSearchClosed } = actionCreators;
const { docTreeReceived } = docExplorerActionCreators;

const defaultState = {
  isOpen: false,
  searchTerm: '',
  searchResults: [],
  rawData: [],
  search: undefined,
  selectedItem: 0, // Used for simple item selection, by array index
  selectedDocRef: undefined, // Used for loading
};

const reducer = handleActions(
  {
    [combineActions(appSearchOpened, appSearchClosed)]: (state, { payload: { isOpen } }) => ({
      ...state,
      isOpen,
      searchTerm: '',
      searchResults: [],
    }),
    APP_SEARCH_TERM_UPDATED: (state, { payload: { searchTerm } }) => {
      let searchResults = [];

      if (state.search) {
        searchResults = state.search.search(searchTerm);
      }

      return {
        ...state,
        searchTerm,
        searchResults,
      };
    },
    APP_SEARCH_SELECTION_UP: (state, payload) => {
      const nextIndex = state.selectedItem === 0 ? 0 : state.selectedItem - 1;
      return {
        ...state,
        selectedItem: nextIndex,
        selectedDocRef: state.searchResults[nextIndex],
      };
    },
    APP_SEARCH_SELECTION_DOWN: (state, payload) => {
      const nextIndex =
        state.selectedItem === state.searchResults.length - 1
          ? state.searchResults.length - 1
          : state.selectedItem + 1;
      return {
        ...state,
        selectedItem: nextIndex,
        selectedDocRef: state.searchResults[nextIndex],
      };
    },
    [docTreeReceived]: (state, { payload: { documentTree } }) => {
      const rawData = [];

      iterateNodes(documentTree, (lineage, node) => {
        rawData.push({
          name: node.name,
          type: node.type,
          uuid: node.uuid,
          lineage,
          lineageNames: lineage.reduce((acc, curr) => `${acc} ${curr.name}`, ''),
        });
      });

      const search = new JsSearch.Search('uuid');
      search.addIndex('name');
      search.addIndex('lineageNames');
      search.addDocuments(rawData);

      return {
        ...state,
        rawData,
        search,
        searchTerm: '',
        searchResults: [],
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
