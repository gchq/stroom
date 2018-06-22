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
