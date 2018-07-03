/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { createActions, handleActions } from 'redux-actions';

// Enumerate the tab types that can be opened
const TAB_TYPES = {
  OPEN_DOC_REFS: 0,
  EXPLORER_TREE: 1,
  TRACKER_DASHBOARD: 2,
  USER_ME: 3,
  AUTH_USERS: 4,
  AUTH_TOKENS: 5,
};

const actionCreators = createActions({
  TAB_WAS_SELECTED: tabType => ({ tabType }),
  DOC_REF_OPENED: docRef => ({ docRef }),
  DOC_REF_TAB_SELECTED: docRef => ({ docRef }),
  DOC_REF_CLOSED: docRef => ({ docRef }),
});

const defaultState = {
  selectedTab: undefined,
  openTabs: [],
  selectedDocRef: undefined,
  docSelectionStack: [],
  openDocRefTabs: [],
};

const reducer = handleActions(
  {
    TAB_WAS_SELECTED: (state, action) => ({
      ...state,
      selectedTab: action.payload.tabType,
      openTabs: state.openTabs
        .filter(t => t !== action.payload.tabType)
        .concat([action.payload.tabType]),
    }),
    DOC_REF_OPENED: (state, action) => {
      const { docRef } = action.payload;

      const docRefIsOpen = state.openDocRefTabs.find(d => d.uuid === docRef.uuid);
      const openDocRefTabs = docRefIsOpen
        ? state.openDocRefTabs
        : state.openDocRefTabs.concat([docRef]);

      return {
        ...state,
        selectedTab: TAB_TYPES.OPEN_DOC_REFS,
        openTabs: state.openTabs
          .filter(t => t !== TAB_TYPES.OPEN_DOC_REFS)
          .concat([TAB_TYPES.OPEN_DOC_REFS]),
        openDocRefTabs,
        selectedDocRef: docRef,
        docSelectionStack: state.docSelectionStack
          .filter(t => t.uuid !== docRef.uuid)
          .concat([docRef]),
      };
    },
    DOC_REF_TAB_SELECTED: (state, action) => {
      const { docRef } = action.payload;

      // The selection action gets fired even when a tab is closed, so only act if its still open
      if (state.openDocRefTabs.find(d => d.uuid === docRef.uuid)) {
        const docSelectionStack = state.docSelectionStack
          .filter(t => t.uuid !== docRef.uuid)
          .concat([docRef]);
        let selectedDocRef;
        if (docSelectionStack.length > 0) {
          selectedDocRef = docSelectionStack[docSelectionStack.length - 1];
        }

        return {
          ...state,
          selectedDocRef,
          docSelectionStack,
        };
      }
      return state;
    },
    DOC_REF_CLOSED: (state, action) => {
      const docRefFilter = t => t.uuid !== action.payload.docRef.uuid;
      const openDocRefTabs = state.openDocRefTabs.filter(docRefFilter);
      const docSelectionStack = state.docSelectionStack.filter(docRefFilter);
      let selectedDocRef;
      if (docSelectionStack.length > 0) {
        selectedDocRef = docSelectionStack[docSelectionStack.length - 1];
      }

      return {
        ...state,
        openDocRefTabs,
        selectedDocRef,
        docSelectionStack,
      };
    },
  },
  defaultState,
);

export { TAB_TYPES, actionCreators, reducer };
