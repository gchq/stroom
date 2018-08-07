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
  DOC_REF_OPENED: docRef => ({ docRef }),
  RECENT_ITEMS_CLOSED: () => ({}),
  RECENT_ITEMS_SELECTION_UP: () => ({}),
  RECENT_ITEMS_SELECTION_DOWN: () => ({}),
});

const defaultState = {
  openItemStack: [],
  selectedItem: 0, // Used for simple item selection, by array index
  selectedDocRef: undefined, // Used for loading
};

const reducer = handleActions(
  {
    RECENT_ITEMS_CLOSED: state => ({
      ...state,
      selectedItem: 0,
      selectedDocRef: undefined,
    }),
    DOC_REF_OPENED: (state, { payload: { docRef } }) => ({
      ...state,
      openItemStack: [docRef].concat(state.openItemStack.filter(d => d.uuid !== docRef.uuid)),
    }),
    RECENT_ITEMS_SELECTION_UP: (state, payload) => {
      const nextIndex = state.selectedItem === 0 ? 0 : state.selectedItem - 1;
      return {
        ...state,
        selectedItem: nextIndex,
        selectedDocRef: state.openItemStack[nextIndex],
      };
    },
    RECENT_ITEMS_SELECTION_DOWN: (state, payload) => {
      const nextIndex =
        state.selectedItem === state.openItemStack.length - 1
          ? state.openItemStack.length - 1
          : state.selectedItem + 1;
      return {
        ...state,
        selectedItem: nextIndex,
        selectedDocRef: state.openItemStack[nextIndex],
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
