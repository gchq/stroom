import { createActions, combineActions, handleActions } from 'redux-actions';

import { updateIdSubstate } from 'lib/reduxFormUtils';

const actionCreators = createActions({
  OPEN_DROPDOWN: pickerId => ({ pickerId, isOpen: true }),
  CLOSE_DROPDOWN: pickerId => ({ pickerId, isOpen: false }),
  SWITCH_MODE: (pickerId, mode) => ({ pickerId, mode }),
  NAVIGATE_TO_FOLDER: (pickerId, navFolder) => ({ pickerId, navFolder }),
  CHOOSE_DOC_REF: (pickerId, chosenDocRef) => ({ pickerId, chosenDocRef }),
  SEARCH_TERM_UPDATED: (pickerId, searchTerm) => ({ pickerId, searchTerm }),
  SEARCH_RESULTS_RETURNED: (pickerId, searchResults) => ({ pickerId, searchResults }),
});

const { openDropdown, closeDropdown } = actionCreators;

const SEARCH_MODE = {
  GLOBAL_SEARCH: 0,
  NAVIGATION: 1,
  RECENT_ITEMS: 2
};

const defaultPickerState = {
  searchTerm: '',
  searchResults: [],
  navFolder: undefined,
  chosenDocRef: undefined,
  isOpen: false,
  searchMode: SEARCH_MODE.NAVIGATION,
};

const defaultState = {};

const reducer = handleActions(
  {
    [combineActions(openDropdown, closeDropdown)]: (state, { payload: { pickerId, isOpen } }) =>
      updateIdSubstate(state, pickerId, defaultPickerState, {
        isOpen,
      }),
    SWITCH_MODE: (state, action) =>
      updateIdSubstate(state, action.payload.pickerId, defaultPickerState, {
        searchMode: action.payload.mode,
      }),
    CHOOSE_DOC_REF: (state, action) =>
      updateIdSubstate(state, action.payload.pickerId, defaultPickerState, {
        chosenDocRef: action.payload.chosenDocRef,
        isOpen: false
      }),
    NAVIGATE_TO_FOLDER: (state, action) =>
      updateIdSubstate(state, action.payload.pickerId, defaultPickerState, {
        navFolder: action.payload.navFolder,
        searchMode: SEARCH_MODE.NAVIGATION,
      }),
    SEARCH_TERM_UPDATED: (state, action) =>
      updateIdSubstate(state, action.payload.pickerId, defaultPickerState, {
        searchTerm: action.payload.searchTerm,
        searchMode:
          action.payload.searchTerm.length > 0 ? SEARCH_MODE.GLOBAL_SEARCH : SEARCH_MODE.NAVIGATION,
      }),
    SEARCH_RESULTS_RETURNED: (state, action) =>
      updateIdSubstate(state, action.payload.pickerId, defaultPickerState, {
        searchResults: action.payload.searchResults,
      }),
  },
  defaultState,
);

export { actionCreators, reducer, defaultPickerState, SEARCH_MODE };
