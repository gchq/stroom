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
import React from 'react';
import { createActions, handleActions } from 'redux-actions';
import { connect } from 'react-redux';
import { branch, compose, renderComponent } from 'recompose';
import { Loader } from 'semantic-ui-react';

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

/**
 * Higher Order Component that ensures that the config has been set before making a call to fetchDocTree.
 */
const withConfigReady = compose(
  connect(
    (state, props) => ({
      configIsReady: state.config.isReady,
    }),
    {},
  ),
  branch(
    ({ configIsReady }) => !configIsReady,
    renderComponent(() => <Loader active>Awaiting Config</Loader>),
  ),
);

export { actionCreators, configReducer, withConfigReady };
