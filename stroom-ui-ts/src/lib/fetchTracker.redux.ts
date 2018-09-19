import { createActions, handleActions } from "redux-actions";
import { push } from "react-router-redux";
import { Dispatch, Action } from "redux";

import { actionCreators as errorActionCreators } from "../components/ErrorPage";
import { GlobalStoreState } from "../startup/reducers";

import handleStatus from "./handleStatus";

export enum FetchState {
  UNREQUESTED,
  REQUESTED,
  RESPONDED,
  FAILED
}

export interface StoreState {
  [s: string]: FetchState;
}

export interface StoreAction {
  url?: string;
  fetchState: FetchState;
}

const baseActionCreators = createActions<StoreAction>({
  URL_SET: (url: string, fetchState: FetchState) => ({ url, fetchState })
});

const defaultState = {};

export const actionCreators = {
  resetAllUrls: (): Action =>
    baseActionCreators.urlSet(undefined, FetchState.UNREQUESTED),
  urlReset: (url: string): Action =>
    baseActionCreators.urlSet(url, FetchState.UNREQUESTED),
  urlRequested: (url: string): Action =>
    baseActionCreators.urlSet(url, FetchState.REQUESTED),
  urlResponded: (url: string): Action =>
    baseActionCreators.urlSet(url, FetchState.RESPONDED),
  urlFailed: (url: string): Action =>
    baseActionCreators.urlSet(url, FetchState.FAILED)
};

export const reducer = handleActions<StoreState, StoreAction>(
  {
    URL_SET: (state, { payload }) => {
      const { url, fetchState } = payload!;
      if (url) {
        return {
          ...state,
          [url]: fetchState
        };
      } else {
        // Clear everything
        return {};
      }
    }
  },
  defaultState
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
export const wrappedGet = (
  dispatch: Dispatch,
  state: GlobalStoreState,
  url: string,
  successCallback: (x: any) => void,
  options?: {
    [s: string]: any;
  },
  forceGet?: boolean
) => {
  const jwsToken = state.authentication.idToken;
  const currentState = state.fetch[url];
  let needToFetch = false;

  // console.group('Requesting ', url);
  // console.log('Current State of URL', { url, currentState });

  if (!forceGet) {
    switch (currentState) {
      case undefined:
        // console.log('Never even heard of it', url);
        needToFetch = true;
        break;
      case FetchState.UNREQUESTED:
        // console.log('Has been reset, go again!', url);
        needToFetch = true;
        break;
      case FetchState.FAILED:
        // console.log('It failed last time, second times a charm?', url);
        needToFetch = true;
        break;
      case FetchState.REQUESTED:
        // console.log('Already asked, dont ask again', url);
        needToFetch = false;
        break;
      case FetchState.RESPONDED:
        // console.log('Already got it, dont ask again', url);
        needToFetch = false;
        break;
      default:
        // console.log('Default state? Nonsense');
        break;
    }
  } else {
    needToFetch = true;
  }

  if (needToFetch) {
    dispatch(actionCreators.urlRequested(url));

    fetch(url, {
      method: "get",
      mode: "cors",
      ...options,
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: `Bearer ${jwsToken}`,
        ...(options ? options.headers : {})
      }
    })
      .then(handleStatus)
      .then(responseBody => {
        dispatch(actionCreators.urlResponded(url));
        successCallback(responseBody);
      })
      .catch(error => {
        dispatch(actionCreators.urlFailed(url));
        dispatch(errorActionCreators.setErrorMessage(error.message));
        dispatch(errorActionCreators.setStackTrace(error.stack));
        dispatch(errorActionCreators.setHttpErrorCode(error.status));
        dispatch(push("/s/error"));
      });
  }

  // console.groupEnd();
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
export const wrappedFetchWithBody = (
  dispatch: Dispatch,
  state: GlobalStoreState,
  url: string,
  successCallback: (x: any) => void,
  options?: {
    [s: string]: any;
  }
) => {
  const jwsToken = state.authentication.idToken;

  const headers = {
    Accept: "application/json",
    "Content-Type": "application/json",
    Authorization: `Bearer ${jwsToken}`,
    ...(options ? options.headers : {})
  };

  fetch(url, {
    mode: "cors",
    ...options,
    headers
  })
    .then(handleStatus)
    .then(response => {
      dispatch(actionCreators.urlResponded(url));
      successCallback(response);
    })
    .catch(error => {
      dispatch(actionCreators.urlFailed(url));
      dispatch(errorActionCreators.setErrorMessage(error.message));
      dispatch(errorActionCreators.setStackTrace(error.stack));
      dispatch(errorActionCreators.setHttpErrorCode(error.status));
      dispatch(push("/s/error"));
    });
};

export const wrappedPost = (
  dispatch: Dispatch,
  state: GlobalStoreState,
  url: string,
  successCallback: (x: any) => void,
  options?: {
    [s: string]: any;
  }
) => {
  wrappedFetchWithBody(dispatch, state, url, successCallback, {
    method: "post",
    ...options
  });
};

export const wrappedPut = (
  dispatch: Dispatch,
  state: GlobalStoreState,
  url: string,
  successCallback: (x: any) => void,
  options?: {
    [s: string]: any;
  }
) => {
  wrappedFetchWithBody(dispatch, state, url, successCallback, {
    method: "put",
    ...options
  });
};

export const wrappedPatch = (
  dispatch: Dispatch,
  state: GlobalStoreState,
  url: string,
  successCallback: (x: any) => void,
  options?: {
    [s: string]: any;
  }
) => {
  wrappedFetchWithBody(dispatch, state, url, successCallback, {
    method: "patch",
    ...options
  });
};
