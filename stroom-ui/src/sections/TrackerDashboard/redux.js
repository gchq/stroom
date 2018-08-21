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

const initialState = {
  trackers: [],
  isLoading: false,
  showCompleted: false,
  sortBy: 'pipelineUuid',
  sortDirection: 'ascending',
  pageSize: 10,
  pageOffset: 0,
  searchCriteria: 'is:incomplete ',
  totalTrackers: 0,
  numberOfPages: 1,
  selectedTrackerId: undefined,
};

export const Directions = Object.freeze({ ascending: 'ascending', descending: 'descending' });
export const SortByOptions = {
  pipelineUuid: 'pipelineUuid',
  priority: 'Priority',
  progress: 'progress',
};

export const actionCreators = createActions({
  UPDATE_SORT: (sortBy, sortDirection) => ({ sortBy, sortDirection }),
  UPDATE_TRACKERS: (streamTasks, totalStreamTasks) => ({
    streamTasks,
    totalStreamTasks,
  }),
  MOVE_SELECTION: direction => ({ direction }),
  UPDATE_ENABLED: (filterId, enabled) => ({ filterId, enabled }),
  UPDATE_TRACKER_SELECTION: filterId => ({ filterId }),
  UPDATE_SEARCH_CRITERIA: searchCriteria => ({ searchCriteria }),
  CHANGE_PAGE: page => ({ page }),
  UPDATE_PAGE_SIZE: pageSize => ({ pageSize }),
  RESET_PAGING: () => ({}),
  PAGE_RIGHT: () => ({}),
  PAGE_LEFT: () => ({}),
  SELECT_FIRST: () => ({}),
  SELECT_LAST: () => ({}),
  SELECT_NONE: () => ({}),
});

const reducers = handleActions(
  {
    UPDATE_SORT: (state, { payload }) => ({
      ...state,
      sortBy: payload.sortBy,
      sortDirection: payload.sortDirection,
    }),
    UPDATE_TRACKERS: (state, { payload }) => ({
      ...state,
      trackers: payload.streamTasks,
      totalTrackers: payload.totalStreamTasks,
      numberOfPages: Math.ceil(payload.totalStreamTasks / state.pageSize),
    }),
    UPDATE_ENABLED: (state, { payload }) => ({
      ...state,
      // TODO: use a filter then a map
      trackers: state.trackers.map((tracker, i) =>
        (tracker.filterId === payload.filterId
          ? { ...tracker, enabled: payload.enabled }
          : tracker)),
    }),
    UPDATE_TRACKER_SELECTION: (state, { payload }) => ({
      ...state,
      selectedTrackerId: payload.filterId,
    }),
    MOVE_SELECTION: (state, { payload }) => {
      const currentIndex = state.trackers.findIndex(tracker => tracker.filterId === state.selectedTrackerId);

      let nextSelectedId;
      if (currentIndex === -1) {
        // There's no selection so we'll leave the selection as undefined
      } else if (payload.direction.toLowerCase() === 'up') {
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
    UPDATE_SEARCH_CRITERIA: (state, { payload }) => {
      let sortBy = state.sortBy;
      if (payload.searchCriteria.includes('sort:next')) {
        sortBy = undefined;
      }
      return {
        ...state,
        searchCriteria: payload.searchCriteria,
        sortBy,
      };
    },
    CHANGE_PAGE: (state, { payload }) => ({
      ...state,
      pageOffset: payload.page,
    }),
    UPDATE_PAGE_SIZE: (state, { payload }) => ({ ...state, pageSize: payload.pageSize }),
    RESET_PAGING: (state, { payload }) => ({
      ...state,
      pageOffset: initialState.pageOffset,
      // This does not reset pageSize because that is managed to be the size of the viewport
    }),
    PAGE_RIGHT: (state, { payload }) => {
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
    PAGE_LEFT: (state, { payload }) => {
      // We don't want to page further than is possible
      const currentPageOffset = state.pageOffset;
      const newPageOffset = currentPageOffset > 0 ? currentPageOffset - 1 : 0;
      return {
        ...state,
        pageOffset: newPageOffset,
      };
    },
    SELECT_FIRST: (state, { payload }) => ({
      ...state,
      selectedTrackerId: state.trackers[0].filterId,
    }),
    SELECT_LAST: (state, { payload }) => ({
      ...state,
      selectedTrackerId: state.trackers[state.trackers.length - 1].filterId,
    }),
    SELECT_NONE: (state, { payload }) => ({ ...state, selectedTrackerId: undefined }),
  },
  initialState,
);

export default reducers;
