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
import { createActionHandlersPerId } from 'lib/reduxFormUtils';

const actionCreators = createActions({
  DICTIONARY_RECEIVED: (dictionaryUuid, dictionary) => ({ dictionaryUuid, dictionary }),
  DICTIONARY_UPDATED: (dictionaryUuid, dictionary) => ({ dictionaryUuid, dictionary }),
  DICTIONARY_SAVED: dictionaryUuid => ({ dictionaryUuid }),
});

const defaultState = {};
const defaultStatePerId = {
  isDirty: false,
  dictionary: undefined,
};

const byXsltId = createActionHandlersPerId(({ payload: { dictionaryUuid } }) => dictionaryUuid, defaultStatePerId);

const reducer = handleActions(
  byXsltId({
    DICTIONARY_RECEIVED: (state, { payload: { dictionary } }) => ({
      dictionary,
      isDirty: false,
    }),
    DICTIONARY_UPDATED: (state, { payload: { dictionary } }) => ({
      dictionary,
      isDirty: true,
    }),
    DICTIONARY_SAVED: (state, { payload: { dictionary } }, currentState) => ({
      currentState,
      isDirty: false,
    }),
  }),
  defaultState,
);

export { actionCreators, reducer };
