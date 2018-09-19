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
import { createActions, handleActions } from "redux-actions";
import { Dispatch } from "redux";
import { GlobalStoreState } from "../startup/reducers";

import { wrappedGet } from "../lib/fetchTracker.redux";

const initialState = { values: {}, isReady: false };

export interface Config {
  authenticationServiceUrl?: string;
  authorisationServiceUrl?: string;
  stroomBaseServiceUrl?: string;
  advertisedUrl?: string;
  authUsersUiUrl?: string;
  authTokensUiUrl?: string;
  appClientId?: string;
}

export interface StoreState {
  isReady: boolean;
  values: Config;
}

export interface StoreAction {
  values: Config;
}

const actionCreators = createActions<StoreAction>({
  UPDATE_CONFIG: values => ({ values })
});

const reducer = handleActions<StoreState, StoreAction>(
  {
    UPDATE_CONFIG: (_, { payload }) => ({
      isReady: true,
      values: payload!.values
    })
  },
  initialState
);

const fetchConfig = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const url = "/config.json";
  wrappedGet(dispatch, getState(), url, response => {
    response.json().then((config: Config) => {
      dispatch(actionCreators.updateConfig(config));
    });
  });
};

export { actionCreators, reducer, fetchConfig };
