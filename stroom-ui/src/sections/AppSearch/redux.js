import { createActions, handleActions, combineActions } from 'redux-actions';
import * as JsSearch from 'js-search';

import { iterateNodes } from 'lib/treeUtils';
import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer/redux';

const { docTreeReceived } = folderExplorerActionCreators;

const actionCreators = createActions({
  APP_SEARCH_TERM_UPDATED: searchTerm => ({ searchTerm }),
});

const { appSearchTermUpdated } = actionCreators;

const defaultState = {
  searchTerm: '',
  searchResults: [],
  documentTree: {
    uuid: 'none',
    type: 'System',
    name: 'None',
  },
};

const reducer = handleActions(
  {
    [combineActions(appSearchTermUpdated, docTreeReceived)]: (
      state,
      { payload: { documentTree, searchTerm } },
    ) => {
      const documentTreeToUse = documentTree || state.documentTree;
      const searchTermToUse = searchTerm || state.searchTerm;
      let searchResults = [];

      const allDocuments = [];
      iterateNodes(documentTreeToUse, (lineage, node) => {
        allDocuments.push({
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
      search.addDocuments(allDocuments);

      if (searchTermToUse && searchTermToUse.length > 1) {
        searchResults = search.search(searchTermToUse).map(s => ({
          node: {
            name: s.name,
            type: s.type,
            uuid: s.uuid,
            lineage: s.lineage,
          },
        }));
      }

      return {
        documentTree: documentTreeToUse,
        searchTerm: searchTermToUse,
        searchResults: searchResults.slice(0, 10),
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
