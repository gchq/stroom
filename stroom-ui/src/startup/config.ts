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
import { Dispatch, Action } from "redux";
import { GlobalStoreState } from "../startup/reducers";
import { prepareReducer } from "../lib/redux-actions-ts";
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

const UPDATE_CONFIG = "UPDATE_CONFIG";

export interface UpdateConfigAction extends Action<"UPDATE_CONFIG"> {
  values: Config;
}

const actionCreators = {
  updateConfig: (values: Config): UpdateConfigAction => ({
    type: UPDATE_CONFIG,
    values
  })
};

const reducer = prepareReducer(initialState)
  .handleAction<UpdateConfigAction>(UPDATE_CONFIG, (state, { values }) => ({
    isReady: true,
    values: values
  }))
  .getReducer();

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
