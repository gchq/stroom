import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  SEARCH_TERM_UPDATED: searchTerm => ({ searchTerm }),
  SEARCH_DOC_REF_TYPE_CHOSEN: searchDocRefType => ({ searchDocRefType }),
  SEARCH_RESULTS_RETURNED: searchResults => ({ searchResults }),
  SEARCH_SELECTION_UP: () => ({}),
  SEARCH_SELECTION_DOWN: () => ({}),
  SEARCH_SELECTION_SET: selectedIndex => ({ selectedIndex }),
});

const NON_SELECTED = -1;

const defaultState = {
  searchTerm: '',
  searchResults: [],
  selectedIndex: NON_SELECTED,
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
    SEARCH_SELECTION_UP: (state, action) => ({
      ...state,
      selectedIndex: state.selectedIndex > 0 ? state.selectedIndex - 1 : NON_SELECTED,
    }),
    SEARCH_SELECTION_DOWN: (state, action) => ({
      ...state,
      selectedIndex: (state.selectedIndex + 1) % state.searchResults.length,
    }),
    SEARCH_SELECTION_SET: (state, { payload: { selectedIndex } }) => ({
      ...state,
      selectedIndex,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
