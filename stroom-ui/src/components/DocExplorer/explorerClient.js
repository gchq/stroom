import React from 'react';
import { push } from 'react-router-redux';
import { connect } from 'react-redux';
import { lifecycle, branch, compose, renderComponent } from 'recompose';
import { actionCreators } from './redux';

import { Loader } from 'semantic-ui-react';

import {
  setErrorMessageAction,
  setStackTraceAction,
  setHttpErrorCodeAction,
} from 'sections/ErrorPage';

import handleStatus from 'lib/handleStatus';

const { docTreeReceived } = actionCreators;

const fetch = window.fetch;

export const fetchDocTree = () => (dispatch, getState) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;

  const url = `${state.config.explorerServiceUrl}/all`;

  fetch(url, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwsToken}`,
    },
    method: 'get',
    mode: 'cors',
  })
    .then(handleStatus)
    .then(response => response.json())
    .then((docTree) => {
      dispatch(docTreeReceived(docTree));
    })
    .catch((error) => {
      dispatch(setErrorMessageAction(error.message));
      dispatch(setStackTraceAction(error.stack));
      dispatch(setHttpErrorCodeAction(error.status));
      dispatch(push('/error'));
    });
};

export const withRemoteDocTreeFetch = compose(
  connect(
    (state, props) => ({
      config: state.config,
    }),
    {
      fetchDocTree,
    },
  ),
  branch(
    ({ config }) => !config.isReady,
    renderComponent(() => <Loader active>Awaiting Config</Loader>),
  ),
  lifecycle({
    componentDidMount() {
      this.props.fetchDocTree();
    },
  }),
);
