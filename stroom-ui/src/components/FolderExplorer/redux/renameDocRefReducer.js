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

import { actionCreators as explorerTreeActionCreators } from 'components/DocExplorer/redux/explorerTreeReducer';

const { docRefRenamed } = explorerTreeActionCreators;

const actionCreators = createActions({
  PREPARE_DOC_REF_RENAME: docRef => ({ docRef }),
  RENAME_UPDATED: name => ({ name }),
  COMPLETE_DOC_REF_RENAME: () => ({ docRef: undefined }),
});

const { prepareDocRefRename, completeDocRefRename } = actionCreators;

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultState = { isRenaming: false, docRef: undefined, name: '' };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefRename, completeDocRefRename)]: (
      state,
      { payload: { docRef } },
    ) => ({
      isRenaming: !!docRef,
      docRef,
      name: docRef ? docRef.name : '',
    }),
    [docRefRenamed]: () => defaultState,
    RENAME_UPDATED: (state, { payload: { name } }) => ({
      ...state,
      name,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
