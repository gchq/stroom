// @flow

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
import { createAction, createActions, handleActions } from 'redux-actions';

import { push } from 'react-router-redux';

import { HttpError } from '../../ErrorTypes';

// This is just for the auth code -- TODO move it
declare type AuthenticationState = {
  idToken: string
};

// This is just for the Config code -- TODO Move it
declare type ConfigState = {
  streamTaskServiceUrl: string
};
export const UPDATE_TRACKERS: string = 'trackerDashboard/UPDATE_TRACKERS';

type UpdateTrackerAction = {
  type: typeof UPDATE_TRACKERS,
  trackers: Array<Tracker>
};

type Action = UpdateTrackerAction;

// These are common to all thunks --TODO move it
type Dispatch = (action: Action | ThunkAction | PromiseAction) => any;
type ThunkAction = (dispatch: Dispatch, getState: GetState) => any;
type GetState = () => StateRoot;
type PromiseAction = Promise<Action>;

// This is common to all reducers --TODO move it
declare type StateRoot = {
  trackerDashboard: TrackerState,
  authentication: AuthenticationState,
  config: ConfigState
};

const fetch = window.fetch;

export const directions = { ascending: 'ascending', descending: 'descending' };
declare type Direction = $Keys<typeof directions>;

export const sortByOptions = { Pipeline: 'Pipeline', Priority: 'Priority', Progress: 'progress' };
declare type SortByOption = $Keys<typeof sortByOptions>;

declare type Tracker = {
  name: string,
  trackerMs: Date,
  trackerPercentage: number
};

type TrackerState = {
  +trackers: Array<Tracker>,
  +isLoading: boolean,
  +showCompleted: boolean,
  +sortBy: SortByOption,
  +sortDirection: Direction,
  +pageSize: number,
  +pageOffset: number,
  +searchCriteria: string,
  totalTrackers: number,
  numberOfPages: number,
  selectedTrackerId: number
};

const initialState: TrackerState = {
  trackers: [],
  isLoading: false,
  showCompleted: false,
  sortBy: 'Pipeline',
  sortDirection: 'ascending',
  pageSize: 10,
  pageOffset: 0,
  searchCriteria: 'is:incomplete ',
  totalTrackers: 0,
  numberOfPages: 0,
  selectedTrackerId: undefined,
};

export const actionCreators = createActions({
  UPDATE_SORT: (sortBy, sortDirection) => ({ sortBy, sortDirection }),
  UPDATE_TRACKERS: (streamTasks, totalStreamTasks) => ({ streamTasks, totalStreamTasks }),
  MOVE_SELECTION: direction => ({ direction }),
  UPDATE_ENABLED: (filterId, enabled) => ({ filterId, enabled }),
  UPDATE_TRACKER_SELECTION: filterId => ({ filterId }),
  UPDATE_SEARCH_CRITERIA: searchCriteria => ({ searchCriteria }),
  CHANGE_PAGE: page => ({ page }),
  UPDATE_PAGE_SIZE: pageSize => ({ pageSize }),
  RESET_PAGING: () => ({}),
  PAGE_RIGHT: () => ({}),
  PAGE_LEFT: () => ({}),
});

const reducers = handleActions(
  {
    UPDATE_SORT: (state, action) => ({
      ...state,
      sortBy: action.payload.sortBy,
      sortDirection: action.payload.sortDirection,
    }),
    UPDATE_TRACKERS: (state, action) => ({
      ...state,
      trackers: action.payload.streamTasks,
      totalTrackers: action.payload.totalStreamTasks,
      numberOfPages: Math.ceil(action.payload.totalStreamTasks / state.pageSize),
    }),
    UPDATE_ENABLED: (state, action) => ({
      ...state,
      // TODO: use a filter then a map
      trackers: state.trackers.map((tracker, i) =>
        (tracker.filterId === action.payload.filterId
          ? { ...tracker, enabled: action.payload.enabled }
          : tracker)),
    }),
    UPDATE_TRACKER_SELECTION: (state, action) => ({
      ...state,
      selectedTrackerId: action.payload.filterId,
    }),
    MOVE_SELECTION: (state, action) => {
      const currentIndex = state.trackers.findIndex(tracker => tracker.filterId === state.selectedTrackerId);

      let nextSelectedId;
      if (currentIndex === -1) {
        // There's no selection so we'll leave the selection as undefined
      } else if (action.payload.direction.toLowerCase() === 'up') {
        if (currentIndex === 0) {
          nextSelectedId = state.trackers[currentIndex].filterId;
        } else {
          nextSelectedId = state.trackers[currentIndex - 1].filterId;
        }
      } else if (currentIndex === state.trackers.length - 1) {
        nextSelectedId = state.trackers[currentIndex].filterId;
      } else {
        nextSelectedId = state.trackers[currentIndex + 1].filterId;
      }
      return {
        ...state,
        selectedTrackerId: nextSelectedId,
      };
    },
    UPDATE_SEARCH_CRITERIA: (state, action) => {
      let sortBy = state.sortBy;
      if (action.payload.searchCriteria.includes('sort:next')) {
        sortBy = undefined;
      }
      return {
        ...state,
        searchCriteria: action.payload.searchCriteria,
        sortBy,
      };
    },
    CHANGE_PAGE: (state, action) => ({
      ...state,
      pageOffset: action.payload.page,
    }),
    UPDATE_PAGE_SIZE: (state, action) => ({ ...state, pageSize: action.payload.pageSize }),
    RESET_PAGING: (state, action) => ({
      ...state,
      pageOffset: initialState.pageOffset,
      // This does not reset pageSize because that is managed to be the size of the viewport
    }),
    PAGE_RIGHT: (state, action) => {
      // We don't want to page further than is possible
      const currentPageOffset = state.pageOffset;
      const numberOfPages = state.numberOfPages - 1;
      const newPageOffset =
        currentPageOffset < numberOfPages ? currentPageOffset + 1 : numberOfPages;
      return {
        ...state,
        pageOffset: newPageOffset,
      };
    },
    PAGE_LEFT: (state, action) => {
      // We don't want to page further than is possible
      const currentPageOffset = state.pageOffset;
      const newPageOffset = currentPageOffset > 0 ? currentPageOffset - 1 : 0;
      return {
        ...state,
        pageOffset: newPageOffset,
      };
    },
  },
  initialState,
);

export default reducers;

export const fetchTrackers = (): ThunkAction => (dispatch, getState) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;

  const rowsToFetch = getRowsPerPage(state.trackerDashboard.selectedTrackerId != undefined);
  dispatch(actionCreators.updatePageSize(rowsToFetch));

  let url = `${state.config.streamTaskServiceUrl}/?`;
  url += `pageSize=${rowsToFetch}`;
  url += `&offset=${state.trackerDashboard.pageOffset}`;
  if (state.trackerDashboard.sortBy != undefined) {
    url += `&sortBy=${state.trackerDashboard.sortBy}`;
    url += `&sortDirection=${state.trackerDashboard.sortDirection}`;
  }

  if (
    state.trackerDashboard.searchCriteria !== '' &&
    state.trackerDashboard.searchCriteria !== undefined
  ) {
    url += `&filter=${state.trackerDashboard.searchCriteria}`;
  }

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
    .then((trackers) => {
      dispatch(actionCreators.updateTrackers(trackers.streamTasks, trackers.totalStreamTasks));
    })
    .catch((error) => {
      // TODO: handle a bad response from the service, i.e. send the use to an error
      dispatch(push('/error'));
      console.log('Unable to fetch trackers!');
      console.log(error);
      this;
    });
};

export const enableToggle = (filterId: string, isCurrentlyEnabled: boolean): ThunkAction => (
  dispatch,
  getState,
) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;
  const url = `${state.config.streamTaskServiceUrl}/${filterId}`;

  fetch(url, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwsToken}`,
    },
    method: 'PATCH',
    mode: 'cors',
    body: JSON.stringify({ op: 'replace', path: 'enabled', value: !isCurrentlyEnabled }),
  })
    .then(handleStatus)
    .then((response) => {
      dispatch(actionCreators.updateEnabled(filterId, !isCurrentlyEnabled));
    })
    .catch((error) => {
      dispatch(push('/error'));
      console.log('Unable to patch tracker!');
      console.log(error);
      this;
    });
};

function handleStatus(response) {
  if (response.status === 200) {
    return Promise.resolve(response);
  }
  return Promise.reject(new HttpError(response.status, response.statusText));
}

export const getRowsPerPage = (isDetailsVisible: boolean) => {
  const viewport = document.getElementById('table-container');
  let rowsInViewport = 20; // Fallback default
  const headerHeight = 46;
  const footerHeight = 36;
  const detailsHeight = 295;
  const rowHeight = 30;
  if (viewport) {
    const viewportHeight = viewport.offsetHeight;
    const heightAvailableForRows = viewportHeight - headerHeight - footerHeight;
    // if (isDetailsVisible) {
    // heightAvailableForRows -= detailsHeight;
    // }
    rowsInViewport = Math.floor(heightAvailableForRows / rowHeight);
  }

  // Make sure we always request at least 1 row, even if the viewport is too small
  // to display it without scrolling. Anything less will be rejected by the
  // service for being rediculous.
  if (rowsInViewport <= 0) {
    return 1;
  }
  return rowsInViewport;
};
