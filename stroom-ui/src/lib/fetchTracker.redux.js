import { createActions, handleActions, combineActions } from 'redux-actions';
import { push } from 'react-router-redux';

import {
  setErrorMessageAction,
  setStackTraceAction,
  setHttpErrorCodeAction,
} from 'sections/ErrorPage';

import handleStatus from 'lib/handleStatus';

export const FETCH_STATES = {
  UNREQUESTED: 0,
  REQUESTED: 1,
  RESPONDED: 2,
  FAILED: 3,
};

export const actionCreators = createActions({
  RESET_ALL_URLS: () => ({}),
  URL_RESET: url => ({ url, fetchState: FETCH_STATES.UNREQUESTED }),
  URL_REQUESTED: url => ({ url, fetchState: FETCH_STATES.REQUESTED }),
  URL_RESPONDED: url => ({ url, fetchState: FETCH_STATES.RESPONDED }),
  URL_FAILED: url => ({ url, fetchState: FETCH_STATES.FAILED }),
});

const {
  urlReset, urlRequested, urlResponded, urlFailed,
} = actionCreators;

const defaultState = {};

export const reducer = handleActions(
  {
    [combineActions(urlReset, urlRequested, urlResponded, urlFailed)](
      state,
      {
        payload: { url, fetchState },
      },
    ) {
      return { ...state, [url]: fetchState };
    },
    RESET_ALL_URLS: (state, action) => ({})
  },
  defaultState,
);

/**
 * A wrapper around fetch that can be used to de-duplicate GET calls to the same resources.
 * It also allows us to track all the URL's which are being requested from remote servers so that we can
 * add a status bar to the UI to indicate how the requests are going.
 *
 * @param {function} dispatch The redux dispatch funciton
 * @param {object} state The current redux state
 * @param {string} url The URL to fetch
 * @param {function} successCallback The function to call with the response if successful. Failures will be handled generically
 */
export const wrappedGet = (dispatch, state, url, successCallback) => {
  const jwsToken = state.authentication.idToken;
  const currentState = state.fetch[url];
  let needToFetch = false;

  console.group('Requesting ', url);
  console.log('Current State of URL', { url, currentState });

  switch (currentState) {
    case undefined:
      console.log('Never even heard of it', url);
      needToFetch = true;
      break;
    case FETCH_STATES.UNREQUESTED:
      console.log('Has been reset, go again!', url);
      needToFetch = true;
      break;
    case FETCH_STATES.FAILED:
      console.log('It failed last time, second times a charm?', url);
      needToFetch = true;
      break;
    case FETCH_STATES.REQUESTED:
      console.log('Already asked, dont ask again', url);
      needToFetch = false;
      break;
    case FETCH_STATES.RESPONDED:
      console.log('Already got it, dont ask again', url);
      needToFetch = false;
      break;
    default:
      console.log('Default state? Nonsense');
      break;
  }

  if (needToFetch) {
    dispatch(urlRequested(url));

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
      .then((responseBody) => {
        dispatch(urlResponded(url));
        successCallback(responseBody);
      })
      .catch((error) => {
        dispatch(urlFailed(url));
        dispatch(setErrorMessageAction(error.message));
        dispatch(setStackTraceAction(error.stack));
        dispatch(setHttpErrorCodeAction(error.status));
        dispatch(push('/error'));
      });
  }

  console.groupEnd();
};

/**
 * Wrapper around fetch that allows us to track all the URL's being requested via the redux store.
 * This will not attempt de-duplication in the same way as the GET one does, because it doesn't make sense for
 * mutating calls to do that.
 *
 * @param {function} dispatch The redux dispatch funciton
 * @param {object} state The current redux state
 * @param {string} url The URL to fetch
 * @param {string} method The HTTP method to use (could be any of the mutating ones, PUT, POST, PATCH etc)
 * @param {object} body The string to send as the request body
 * @param {function} successCallback The function to call with the response if successful. Failures will be handled
 */
export const wrappedFetchWithBody = (dispatch, state, url, method, body, successCallback) => {
  const jwsToken = state.authentication.idToken;

  fetch(url, {
    body: JSON.stringify(body),
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwsToken}`,
    },
    method,
    mode: 'cors',
  })
    .then(handleStatus)
    .then(response => response.json())
    .then((response) => {
      dispatch(urlResponded(url));
      successCallback(response);
    })
    .catch((error) => {
      dispatch(urlFailed(url));
      dispatch(setErrorMessageAction(error.message));
      dispatch(setStackTraceAction(error.stack));
      dispatch(setHttpErrorCodeAction(error.status));
      dispatch(push('/error'));
    });
};

export const wrappedPost = (dispatch, state, url, body, successCallback) => {
  wrappedFetchWithBody(dispatch, state, url, 'post', body, successCallback);
};

export const wrappedPatch = (dispatch, state, url, body, successCallback) => {
  wrappedFetchWithBody(dispatch, state, url, 'patch', body, successCallback);
};
