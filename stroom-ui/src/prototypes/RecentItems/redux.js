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
import { createActions, combineActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  DOC_REF_OPENED: docRef => ({ docRef }),
  RECENT_ITEMS_OPENED: () => ({ isOpen: true }),
  RECENT_ITEMS_CLOSED: () => ({ isOpen: false }),
});

const { recentItemsOpened, recentItemsClosed } = actionCreators;

const defaultState = {
  isOpen: false,
  openItemStack: [],
};

const reducer = handleActions(
  {
    [combineActions(recentItemsOpened, recentItemsClosed)]: (state, { payload: { isOpen } }) => ({
      ...state,
      isOpen,
    }),
    DOC_REF_OPENED: (state, { payload: { docRef } }) => ({
      ...state,
      openItemStack: [docRef].concat(state.openItemStack.filter(d => d.uuid !== docRef.uuid)),
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
