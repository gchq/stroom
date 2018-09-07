import { createActions, handleActions } from 'redux-actions';

import { createActionHandlersPerId } from 'lib/reduxFormUtils';

const actionCreators = createActions({
  SWITCH_MODE: (pickerId, searchMode) => ({ pickerId, searchMode }),
  NAVIGATE_TO_FOLDER: (pickerId, navFolder) => ({ pickerId, navFolder }),
  SEARCH_TERM_UPDATED: (pickerId, searchTerm) => ({ pickerId, searchTerm }),
  SEARCH_RESULTS_RETURNED: (pickerId, searchResults) => ({ pickerId, searchResults }),
});

const SEARCH_MODE = {
  GLOBAL_SEARCH: 0,
  NAVIGATION: 1,
  RECENT_ITEMS: 2,
};

const defaultPickerState = {
  searchTerm: '',
  searchResults: [],
  navFolder: undefined,
  searchMode: SEARCH_MODE.NAVIGATION,
};

const defaultState = {};

const byPickerId = createActionHandlersPerId(
  ({ payload: { pickerId } }) => pickerId,
  defaultPickerState,
);

const reducer = handleActions(
  byPickerId({
    SWITCH_MODE: (state, { payload: { searchMode } }, current) => ({
      searchMode,
    }),
    NAVIGATE_TO_FOLDER: (state, { payload: { navFolder } }, current) => ({
      navFolder,
      searchMode: SEARCH_MODE.NAVIGATION,
    }),
    SEARCH_TERM_UPDATED: (state, { payload: { searchTerm } }, current) => ({
      searchTerm,
      searchMode: searchTerm.length > 0 ? SEARCH_MODE.GLOBAL_SEARCH : SEARCH_MODE.NAVIGATION,
    }),
    SEARCH_RESULTS_RETURNED: (state, { payload: { searchResults } }, current) => ({
      searchResults,
    }),
  }),
  defaultState,
);

export { actionCreators, reducer, defaultPickerState, SEARCH_MODE };
