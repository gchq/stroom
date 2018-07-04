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

const actionCreators = createActions({
  TAB_OPENED: (type, tabId, data) => ({ type, tabId, data }),
  TAB_SELECTED: tabId => ({ tabId }),
  TAB_CLOSED: tabId => ({ tabId }),
});

const defaultState = {
  openTabs: [], // this will remember the tabs in the order they were opened
  tabSelectionStack: [], // this will keep track of the order in which they were navigated to
  recentItemsOpen: false,
};

const reducer = handleActions(
  {
    TAB_SELECTED: (state, action) => {
      const openTabs = state.openTabs.filter(t => t.tabId === action.payload.tabId);
      if (openTabs.length > 0) {
        return {
          ...state,
          tabSelectionStack: openTabs.concat(state.tabSelectionStack.filter(t => t.tabId !== action.payload.tabId)),
        };
      }
      return {
        ...state,
      };
    },
    TAB_OPENED: (state, action) => {
      const tabId = action.payload.tabId || action.payload.type;
      const tabData = [
        {
          type: action.payload.type,
          tabId,
          data: action.payload.data,
        },
      ];
      if (state.openTabs.find(t => t.tabId === tabId)) {
        return {
          ...state,
          tabSelectionStack: tabData.concat(state.tabSelectionStack.filter(t => t.tabId !== tabId)),
        };
      }
      return {
        ...state,
        tabSelectionStack: tabData.concat(state.tabSelectionStack.filter(t => t.tabId !== tabId)),
        openTabs: state.openTabs.concat(tabData),
      };
    },
    TAB_CLOSED: (state, action) => ({
      ...state,
      tabSelectionStack: state.tabSelectionStack.filter(t => t.tabId !== action.payload.tabId),
      openTabs: state.openTabs.filter(t => t.tabId !== action.payload.tabId),
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
