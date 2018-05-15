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

export const UPDATE_CONFIG = 'config/UPDATE_CONFIG';

const initialState = {};

export default (state = initialState, action) => {
  switch (action.type) {
    case UPDATE_CONFIG:
      return {
        ...state,
        authenticationServiceUrl: action.config.authenticationServiceUrl,
        authorisationServiceUrl: action.config.authorisationServiceUrl,
        streamTaskServiceUrl: action.config.streamTaskServiceUrl,
        advertisedUrl: action.config.advertisedUrl,
        appClientId: action.config.appClientId,
      };
    default:
      return state;
  }
};

function updateConfig(config) {
  return {
    type: UPDATE_CONFIG,
    config,
  };
}

export const fetchConfig = () => (dispatch) => {
  fetch('/config.json', { method: 'get' })
    .then(response => response.json())
    .then(config => dispatch(updateConfig(config)));
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
