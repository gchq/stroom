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

import { Action } from "redux";

const initialState = {
  errorMessage: "",
  stackTrace: "",
  httpErrorCode: 0
};

export interface StoreState {
  errorMessage: string;
  stackTrace: string;
  httpErrorCode: number;
}

const SET_ERROR_MESSAGE = "SET_ERROR_MESSAGE";
export interface ISetErrorMessage {
  type: typeof SET_ERROR_MESSAGE;
  errorMessage: string;
}
const setErrorMessage = (errorMessage: string): ISetErrorMessage => ({
  type: SET_ERROR_MESSAGE,
  errorMessage
});
function isSetErrorMessage(action: Action): action is ISetErrorMessage {
  return action.type == SET_ERROR_MESSAGE;
}

const SET_STACK_TRACE = "SET_STACK_TRACE";
export interface ISetStackTrace {
  type: typeof SET_STACK_TRACE;
  stackTrace: string;
}
const setStackTrace = (stackTrace: string): ISetStackTrace => ({
  type: SET_STACK_TRACE,
  stackTrace
});
function isSetStackTrace(action: Action): action is ISetStackTrace {
  return action.type == SET_STACK_TRACE;
}

const SET_HTTP_ERROR_CODE = "SET_HTTP_ERROR_CODE";
export interface ISetHttpErrorCode {
  type: typeof SET_HTTP_ERROR_CODE;
  httpErrorCode: number;
}
const setHttpErrorCode = (httpErrorCode: number): ISetHttpErrorCode => ({
  type: SET_HTTP_ERROR_CODE,
  httpErrorCode
});
function isSetHttpErrorCode(action: Action): action is ISetHttpErrorCode {
  return action.type == SET_HTTP_ERROR_CODE;
}

export type StoreAction = ISetErrorMessage | ISetStackTrace | ISetHttpErrorCode;

export interface ActionCreators {
  setErrorMessage: (errorMessage: string) => ISetErrorMessage;
  setStackTrace: (stackTrace: string) => ISetStackTrace;
  setHttpErrorCode: (httpErrorCode: number) => ISetHttpErrorCode;
}

export const actionCreators: ActionCreators = {
  setErrorMessage,
  setStackTrace,
  setHttpErrorCode
};

export const reducer = (
  state: StoreState = initialState,
  action: StoreAction
): StoreState => {
  if (isSetErrorMessage(action)) {
    return { ...state, errorMessage: action.errorMessage };
  } else if (isSetStackTrace(action)) {
    return {
      ...state,
      stackTrace: action.stackTrace
    };
  } else if (isSetHttpErrorCode(action)) {
    return {
      ...state,
      httpErrorCode: action.httpErrorCode
    };
  }

  return state;
};
