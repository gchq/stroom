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
import { guid } from 'lib/treeUtils';

// Enumerate the tab types that can be opened
const TAB_TYPES = {
  DOC_REF: 0,
  EXPLORER_TREE: 1,
};

const actionCreators = createActions({
  TAB_OPENED: (type, data) => ({ type, data }),
  TAB_CLOSED: tabUuid => ({ tabUuid }),
});

const defaultState = {
  openTabs: [],
};

const reducer = handleActions(
  {
    TAB_OPENED: (state, action) => ({
      ...state,
      openTabs: state.openTabs.concat([
        {
          tabUuid: guid(),
          ...action.payload,
        },
      ]),
    }),
    TAB_CLOSED: (state, action) => ({
      ...state,
      openTabs: state.openTabs.filter(t => t.tabUuid !== action.payload.tabUuid),
    }),
  },
  defaultState,
);

export { TAB_TYPES, actionCreators, reducer };
