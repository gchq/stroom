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

import { actionCreators as documentTreeActionCreators } from './documentTree';

const { docRefsDeleted } = documentTreeActionCreators;

const actionCreators = createActions({
  PREPARE_DOC_REF_DELETE: uuids => ({ uuids }),
  COMPLETE_DOC_REF_DELETE: () => ({ uuids: [] }),
});

const { prepareDocRefDelete, completeDocRefDelete } = actionCreators;

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultState = { isDeleting: false, uuids: [] };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefDelete, completeDocRefDelete)]: (
      state,
      { payload: { uuids } },
    ) => ({
      isDeleting: uuids.length > 0,
      uuids,
    }),
    [docRefsDeleted]: () => defaultState,
  },
  defaultState,
);

export { actionCreators, reducer };
