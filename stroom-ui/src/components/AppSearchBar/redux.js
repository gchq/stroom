import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  SEARCH_RESULTS_RETURNED: searchResults => ({ searchResults }),
});

const defaultState = {
  searchResults: [],
};

const reducer = handleActions(
  {
    SEARCH_RESULTS_RETURNED: (state, { payload: { searchResults } }) => ({
      searchResults,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
