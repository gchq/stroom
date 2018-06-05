import { push } from 'react-router-redux';

import { pipelineReceived } from './redux';

import {
  setErrorMessageAction,
  setStackTraceAction,
  setHttpErrorCodeAction,
} from 'sections/ErrorPage';

import handleStatus from 'lib/handleStatus';

const fetch = window.fetch;

export const fetchPipeline = pipelineId => (dispatch, getState) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;

  const url = `${state.config.pipelineServiceUrl}/${pipelineId}`;

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
    .then((pipeline) => {
      dispatch(pipelineReceived(pipelineId, pipeline));
    })
    .catch((error) => {
      dispatch(setErrorMessageAction(error.message));
      dispatch(setStackTraceAction(error.stack));
      dispatch(setHttpErrorCodeAction(error.status));
      dispatch(push('/error'));
    });
};
