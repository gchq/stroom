import { createActions, handleActions, combineActions } from 'redux-actions';
import * as JsSearch from 'js-search';

import { iterateNodes } from 'lib/treeUtils';
import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';

const actionCreators = createActions({
  APP_SEARCH_TERM_UPDATED: searchTerm => ({ searchTerm }),
  APP_SEARCH_OPENED: () => ({ isOpen: true }),
  APP_SEARCH_CLOSED: () => ({ isOpen: false }),
});

const { appSearchOpened, appSearchClosed } = actionCreators;
const { docTreeReceived } = docExplorerActionCreators;

const defaultState = {
  isOpen: false,
  searchTerm: '',
  searchResults: [],
  rawData: [],
  search: undefined,
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
