import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  SEARCH_TERM_UPDATED: searchTerm => ({ searchTerm }),
  SEARCH_DOC_REF_TYPE_CHOSEN: searchDocRefType => ({ searchDocRefType }),
  SEARCH_RESULTS_RETURNED: searchResults => ({ searchResults }),
});

const defaultState = {
  searchTerm: '',
  searchResults: [],
};

const reducer = handleActions(
  {
    SEARCH_TERM_UPDATED: (state, { payload: { searchTerm } }) => ({
      ...state,
      searchTerm,
      searchDocRefType: undefined,
    }),
    SEARCH_DOC_REF_TYPE_CHOSEN: (state, { payload: { searchDocRefType } }) => ({
      ...state,
      searchTerm: '',
      searchDocRefType,
    }),
    SEARCH_RESULTS_RETURNED: (state, { payload: { searchResults } }) => ({
      ...state,
      searchResults,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
