import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  SEARCH_TERM_UPDATED: (pickerId, searchTerm) => ({ pickerId, searchTerm }),
  SEARCH_RESULTS_RETURNED: (pickerId, searchResults) => ({ pickerId, searchResults }),
});

const defaultPickerState = {
  searchTerm: '',
  searchResults: [],
};

const defaultState = {};

const reducer = handleActions(
  {
    SEARCH_TERM_UPDATED: (state, action) => ({
      ...state,
      [action.payload.pickerId]: {
        ...defaultPickerState,
        ...state[action.payload.pickerId],
        searchTerm: action.payload.searchTerm,
        searchDocRefType: undefined,
      },
    }),
    SEARCH_RESULTS_RETURNED: (state, action) => ({
      ...state,
      [action.payload.pickerId]: {
        ...defaultPickerState,
        ...state[action.payload.pickerId],
        searchResults: action.payload.searchResults,
      },
    }),
  },
  defaultState,
);

export { actionCreators, reducer, defaultPickerState };
