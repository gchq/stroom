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

import { actionCreators as explorerTreeActionCreators } from './explorerTreeReducer';

const { docRefsCopied } = explorerTreeActionCreators;

const actionCreators = createActions({
  PREPARE_DOC_REF_COPY: uuids => ({ uuids }),
  COMPLETE_DOC_REF_COPY: () => ({ uuids: [] }),
});

const { prepareDocRefCopy, completeDocRefCopy } = actionCreators;

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultState = { isCopying: false, uuids: [] };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefCopy, completeDocRefCopy)]: (state, { payload: { uuids } }) => ({
      isCopying: uuids.length > 0,
      uuids,
    }),
    [docRefsCopied]: (state, action) => ({
      isCopying: false,
      uuids: [],
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
