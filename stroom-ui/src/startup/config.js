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

import $ from 'jquery';

import { createActions, handleActions } from 'redux-actions';

const initialState = { isReady: false };

const actionCreators = createActions({
  UPDATE_CONFIG: config => ({ config }),
  CLEAR_CONFIG: () => ({}),
});

const configReducer = handleActions(
  {
    UPDATE_CONFIG: (state, action) => ({
      ...state,
      ...action.payload.config,
      isReady: true,
    }),
    CLEAR_CONFIG: (state, action) => ({
      isReady: false,
    }),
  },
  initialState,
);

export const fetchConfig = () => (dispatch) => {
  fetch('/config.json', { method: 'get' })
    .then(response => response.json())
    .then(config => dispatch(actionCreators.updateConfig(config)));
};

export const fetchConfigSynchronously = () => {
  // This causes a console warning about synchronous calls degrading the user experience.
  // In our case it's necessary -- we must retrieve the config before we bootstrap react and
  // synchronous is the only way.
  const result = $.ajax({
    url: '/config.json',
    contentType: 'application/json',
    dataType: 'json',
    method: 'GET',
    async: false,
  });

  return result.responseJSON;
};

export { actionCreators, configReducer };
