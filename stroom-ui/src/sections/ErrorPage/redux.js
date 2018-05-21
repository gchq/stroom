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

import { createAction, handleActions } from 'redux-actions';

const initialState = {
  errorMessage: '',
  stackTrace: '',
  httpErrorCode: 0,
};

export const setErrorMessageAction = createAction('SET_ERROR_MESSAGE', errorMessage => ({
  errorMessage,
}));

export const setStackTraceAction = createAction('SET_STACK_TRACE', stackTrace => ({
  stackTrace,
}));

export const setHttpErrorCodeAction = createAction('SET_HTTP_ERROR_CODE', httpErrorCode => ({
  httpErrorCode,
}));

const reducers = handleActions(
  {
    SET_ERROR_MESSAGE: (state, { payload }) => ({
      ...state,
      errorMessage: payload.errorMessage,
    }),
    SET_STACK_TRACE: (state, { payload }) => ({
      ...state,
      stackTrace: payload.stackTrace,
    }),
    SET_HTTP_ERROR_CODE: (state, { payload }) => ({
      ...state,
      httpErrorCode: payload.httpErrorCode,
    }),
  },
  initialState,
);

export default reducers;
