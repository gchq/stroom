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
import { createActions, handleActions, combineActions } from 'redux-actions';

const actionCreators = createActions({
  DOC_REF_OPENED: docRef => ({ docRef }),
  RECENT_ITEMS_CLOSED: () => ({ selectedItem: 0, selectedDocRef: undefined }),
  FILTER_TERM_UPDATED: filterTerm => ({ filterTerm }),
  RECENT_ITEMS_SELECTION_UP: () => ({ selectionChange: -1 }),
  RECENT_ITEMS_SELECTION_DOWN: () => ({ selectionChange: +1 }),
});

const {
  docRefOpened,
  filterTermUpdated,
  recentItemsClosed,
  recentItemsSelectionUp,
  recentItemsSelectionDown,
} = actionCreators;

const defaultState = {
  openItemStack: [],
  filteredItemStack: [],
  selectedItem: 0, // Used for simple item selection, by array index
  selectedDocRef: undefined, // Used for loading
  filterTerm: '',
};

const reducer = handleActions(
  {
    [combineActions(docRefOpened, filterTermUpdated, recentItemsClosed)]: (
      state,
      { payload: { docRef, filterTerm = '' } },
    ) => {
      let openItemStack = state.openItemStack;
      if (docRef) {
        openItemStack = [docRef].concat(state.openItemStack.filter(d => d.uuid !== docRef.uuid));
      }
      const filteredItemStack = openItemStack;

      const selectedItem = state.selectedItem % filteredItemStack.length;
      const selectedDocRef =
        filteredItemStack.length > 0 ? filteredItemStack[selectedItem] : undefined;

      return {
        selectedItem,
        selectedDocRef,
        openItemStack,
        filteredItemStack,
        filterTerm,
      };
    },
    [combineActions(recentItemsSelectionUp, recentItemsSelectionDown)]: (
      state,
      { payload: { selectionChange } },
    ) => {
      const nextIndex =
        (state.filteredItemStack.length + (state.selectedItem + selectionChange)) %
        state.filteredItemStack.length;

      return {
        ...state,
        selectedItem: nextIndex,
        selectedDocRef: state.filteredItemStack[nextIndex],
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
