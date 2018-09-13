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

import { createActionHandlerPerId } from 'lib/reduxFormUtils';
import { actionCreators as documentTreeActionCreators } from './documentTree';

const { docRefRenamed } = documentTreeActionCreators;

const actionCreators = createActions({
  PREPARE_DOC_REF_RENAME: (listingId, docRef) => ({ listingId, docRef }),
  COMPLETE_DOC_REF_RENAME: listingId => ({ listingId, docRef: undefined }),
});

const { prepareDocRefRename, completeDocRefRename } = actionCreators;

// listings, keyed on ID, there may be several on a page
const defaultState = {};

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultListingState = { isRenaming: false, docRef: undefined, name: '' };

const byListingId = createActionHandlerPerId(
  ({ payload: { listingId } }) => listingId,
  defaultListingState,
);

const reducer = handleActions(
  {
    [combineActions(prepareDocRefRename, completeDocRefRename)]: byListingId((state, { payload: { docRef } }) => ({
      isRenaming: !!docRef,
      docRef,
      name: docRef ? docRef.name : '',
    })),
    [docRefRenamed]: () => defaultState,
  },
  defaultState,
);

export { actionCreators, reducer, defaultListingState };
