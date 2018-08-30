import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  NAVIGATE_TO_FOLDER: (pickerId, navFolder) => ({ pickerId, navFolder }),
  SEARCH_TERM_UPDATED: (pickerId, searchTerm) => ({ pickerId, searchTerm }),
  SEARCH_RESULTS_RETURNED: (pickerId, searchResults) => ({ pickerId, searchResults }),
});

const SEARCH_MODE = {
  GLOBAL_SEARCH: 0,
  NAVIGATION: 1,
};

const defaultPickerState = {
  searchTerm: '',
  searchResults: [],
  navFolder: undefined,
  searchMode: SEARCH_MODE.NAVIGATION,
};

const defaultState = {};

const updatePicker = (state, action, updates) => ({
  ...state,
  [action.payload.pickerId]: {
    ...defaultPickerState,
    ...state[action.payload.pickerId],
    ...updates,
  },
});

const reducer = handleActions(
  {
    NAVIGATE_TO_FOLDER: (state, action) =>
      updatePicker(state, action, {
        navFolder: action.payload.navFolder,
        searchMode: SEARCH_MODE.NAVIGATION,
      }),
    SEARCH_TERM_UPDATED: (state, action) =>
      updatePicker(state, action, {
        searchTerm: action.payload.searchTerm,
        searchMode:
          action.payload.searchTerm.length > 0 ? SEARCH_MODE.GLOBAL_SEARCH : SEARCH_MODE.NAVIGATION,
      }),
    SEARCH_RESULTS_RETURNED: (state, action) =>
      updatePicker(state, action, {
        searchResults: action.payload.searchResults,
      }),
  },
  defaultState,
);

export { actionCreators, reducer, defaultPickerState, SEARCH_MODE };
