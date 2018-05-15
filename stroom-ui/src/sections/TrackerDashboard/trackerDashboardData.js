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
import { createActions, handleActions } from 'redux-actions';

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
type Dispatch = (action: Action | ThunkAction | PromiseAction) => any; // eslint-disable-line no-use-before-define
type ThunkAction = (dispatch: Dispatch, getState: GetState) => any; // eslint-disable-line no-use-before-define
type GetState = () => StateRoot;
type PromiseAction = Promise<Action>;

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

// This is common to all reducers --TODO move it
declare type StateRoot = {
  trackerDashboard: TrackerState,
  authentication: AuthenticationState,
  config: ConfigState
};

export const directions = { ascending: 'ascending', descending: 'descending' };
declare type Direction = $Keys<typeof directions>;

export const sortByOptions = { Pipeline: 'Pipeline', Priority: 'Priority', Progress: 'progress' };
declare type SortByOption = $Keys<typeof sortByOptions>;

declare type Tracker = {
  name: string,
  trackerMs: Date,
  trackerPercentage: number
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
