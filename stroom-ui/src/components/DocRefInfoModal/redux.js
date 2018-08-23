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
import {
  createActions,
  handleActions,
} from 'redux-actions';

const actionCreators = createActions({
  DOC_REF_INFO_RECEIVED: docRefInfo => ({ docRefInfo }),
  DOC_REF_INFO_OPENED: docRef => ({ docRef }),
  DOC_REF_INFO_CLOSED: () => ({}),
});

// The state will contain the current doc ref for which information is being shown,
// plus a map of all the infos retrieved thus far, keyed on their UUID
const defaultState = {
  isOpen: false,
  docRefInfo: undefined,
  docRefInfoCache: {}, // keyed on UUID
};

const reducer = handleActions(
  {
    DOC_REF_INFO_RECEIVED: (state, { payload: { docRefInfo } }) => ({
      ...state,
      docRefInfo,
      docRefInfoCache: {
        ...state.docRefInfoCache,
        [docRefInfo.docRef.uuid]: docRefInfo,
      },
    }),
    DOC_REF_INFO_OPENED: (state, { payload: { docRef } }) => ({
      isOpen: true,
      docRefInfo: state.docRefInfoCache[docRef.uuid],
    }),
    DOC_REF_INFO_CLOSED: (state, action) => ({
      ...state,
      isOpen: false,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
