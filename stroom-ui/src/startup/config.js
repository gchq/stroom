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
import { push } from 'react-router-redux';
import { createActions, handleActions } from 'redux-actions';
import { connect } from 'react-redux';
import { branch, compose, renderComponent, lifecycle } from 'recompose';
import { Loader } from 'semantic-ui-react';

import {
  setErrorMessageAction,
  setStackTraceAction,
  setHttpErrorCodeAction,
} from 'sections/ErrorPage';

import handleStatus from 'lib/handleStatus';

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

export const fetchConfig = () => (dispatch, getState) => {
  fetch('/config.json', { method: 'get' })
    .then(handleStatus)
    .then(response => response.json())
    .then(config => dispatch(actionCreators.updateConfig(config)))
    .catch((error) => {
      dispatch(setErrorMessageAction(error.message));
      dispatch(setStackTraceAction(error.stack));
      dispatch(setHttpErrorCodeAction(error.status));
      dispatch(push('/error'));
    });
};

/**
 * Higher Order Component that ensures that the config has been set before making a call to fetchDocTree.
 * Used by components which are deep into the application, that aren't responsible for actually loading the config.
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

/**
 * Higher Order Component that kicks off the fetch of the config, and waits by rendering a Loader until
 * that config is returned. This will generally be used by top level components in the app.
 */
const requestConfigAndWait = compose(
  connect(
    (state, props) => ({
      configIsReady: state.config.isReady,
    }),
    {
      fetchConfig,
    },
  ),
  lifecycle({
    componentDidMount() {
      this.props.fetchConfig();
    },
  }),
  branch(
    ({ configIsReady }) => !configIsReady,
    renderComponent(() => <Loader active>Awaiting Config</Loader>),
  ),
);

export { actionCreators, configReducer, withConfigReady, requestConfigAndWait };
