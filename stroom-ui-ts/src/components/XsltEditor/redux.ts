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
import { createActionHandlersPerId } from '../../lib/reduxFormUtils';

const actionCreators = createActions({
  XSLT_RECEIVED: (xsltUuid, xsltData) => ({ xsltUuid, xsltData }),
  XSLT_UPDATED: (xsltUuid, xsltData) => ({ xsltUuid, xsltData }),
  XSLT_SAVED: xsltUuid => ({ xsltUuid }),
});

const defaultState = {};
const defaultStatePerId = {
  isDirty: false,
  xsltData: undefined,
};

const byXsltId = createActionHandlersPerId(({ payload: { xsltUuid } }) => xsltUuid, defaultStatePerId);

const reducer = handleActions(
  byXsltId({
    XSLT_RECEIVED: (state, { payload: { xsltData } }) => ({
      xsltData,
      isDirty: false,
    }),
    XSLT_UPDATED: (state, { payload: { xsltData } }) => ({
      xsltData,
      isDirty: true,
    }),
    XSLT_SAVED: (state, { payload: { xsltData } }, currentState) => ({
      currentState,
      isDirty: false,
    }),
  }),
  defaultState,
);

export { actionCreators, reducer };
