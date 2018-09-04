import { createActions, handleActions } from 'redux-actions';

import { createActionHandlerPerId } from 'lib/reduxFormUtils';

const actionCreators = createActions({
  SWITCH_MODE: (pickerId, mode) => ({ pickerId, mode }),
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

const byPickerId = createActionHandlerPerId(
  ({ payload: pickerId }) => pickerId,
  defaultPickerState,
);

const reducer = handleActions(
  {
    SWITCH_MODE: byPickerId((state, action, currentStateForId) => ({
      searchMode: action.payload.mode,
    })),
    NAVIGATE_TO_FOLDER: byPickerId((state, action, currentStateForId) => ({
      navFolder: action.payload.navFolder,
      searchMode: SEARCH_MODE.NAVIGATION,
    })),
    SEARCH_TERM_UPDATED: byPickerId((state, action, currentStateForId) => ({
      searchTerm: action.payload.searchTerm,
      searchMode:
        action.payload.searchTerm.length > 0 ? SEARCH_MODE.GLOBAL_SEARCH : SEARCH_MODE.NAVIGATION,
    })),
    SEARCH_RESULTS_RETURNED: byPickerId((state, action, currentStateForId) => ({
      searchResults: action.payload.searchResults,
    })),
  },
  defaultState,
);

export { actionCreators, reducer, defaultPickerState, SEARCH_MODE };
