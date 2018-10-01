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
import { Action } from "redux";

import { prepareReducer } from "../../lib/redux-actions-ts";
import { StreamTaskType } from "../../types";

export enum Directions {
  ascending = "ascending",
  descending = "descending"
}
export enum SortByOptions {
  pipelineUuid = "pipelineUuid",
  priority = "Priority",
  progress = "progress"
}

export const ADD_TRACKERS = "ADD_TRACKERS";
export const UPDATE_SORT = "UPDATE_SORT";
export const UPDATE_TRACKERS = "UPDATE_TRACKERS";
export const MOVE_SELECTION = "MOVE_SELECTION";
export const UPDATE_ENABLED = "UPDATE_ENABLED";
export const UPDATE_TRACKER_SELECTION = "UPDATE_TRACKER_SELECTION";
export const UPDATE_SEARCH_CRITERIA = "UPDATE_SEARCH_CRITERIA";
export const CHANGE_PAGE = "CHANGE_PAGE";
export const UPDATE_PAGE_SIZE = "UPDATE_PAGE_SIZE";
export const RESET_PAGING = "RESET_PAGING";
export const PAGE_RIGHT = "PAGE_RIGHT";
export const PAGE_LEFT = "PAGE_LEFT";
export const SELECT_FIRST = "SELECT_FIRST";
export const SELECT_LAST = "SELECT_LAST";
export const SELECT_NONE = "SELECT_NONE";

export interface AddTrackersAction extends Action<"ADD_TRACKERS"> {
  streamTasks: Array<StreamTaskType>;
  totalStreamTasks: number;
}
export interface UpdateSortAction extends Action<"UPDATE_SORT"> {
  sortBy: SortByOptions;
  sortDirection: Directions;
}
export interface UpdateTrackersAction extends Action<"UPDATE_TRACKERS"> {
  streamTasks: Array<StreamTaskType>;
  totalStreamTasks: number;
}
export interface MoveSelectionAction extends Action<"MOVE_SELECTION"> {
  direction: Directions;
}
export interface UpdateEnabledAction extends Action<"UPDATE_ENABLED"> {
  filterId: number;
  enabled: boolean;
}
export interface UpdateTrackerSelectionAction
  extends Action<"UPDATE_TRACKER_SELECTION"> {
  filterId: number;
}
export interface UpdateSearchCriteriaAction
  extends Action<"UPDATE_SEARCH_CRITERIA"> {
  searchCriteria: string;
}
export interface ChangePageAction extends Action<"CHANGE_PAGE"> {
  pageOffset: number;
}
export interface UpdatePageSizeAction extends Action<"UPDATE_PAGE_SIZE"> {
  pageSize: number;
}
export interface ResetPagingAction extends Action<"RESET_PAGING"> {}
export interface PageRightAction extends Action<"PAGE_RIGHT"> {}
export interface PageLeftAction extends Action<"PAGE_LEFT"> {}
export interface SelectFirstAction extends Action<"SELECT_FIRST"> {}
export interface SelectLastAction extends Action<"SELECT_LAST"> {}
export interface SelectNoneAction extends Action<"SELECT_NONE"> {}

export const actionCreators = {
  addTrackers: (
    streamTasks: Array<StreamTaskType>,
    totalStreamTasks: number
  ): AddTrackersAction => ({
    type: ADD_TRACKERS,
    streamTasks,
    totalStreamTasks
  }),
  updateSort: (
    sortBy: SortByOptions,
    sortDirection: Directions
  ): UpdateSortAction => ({
    type: UPDATE_SORT,
    sortBy,
    sortDirection
  }),
  updateTrackers: (
    streamTasks: Array<StreamTaskType>,
    totalStreamTasks: number
  ): UpdateTrackersAction => ({
    type: UPDATE_TRACKERS,
    streamTasks,
    totalStreamTasks
  }),
  moveSelection: (direction: Directions): MoveSelectionAction => ({
    type: MOVE_SELECTION,
    direction
  }),
  updateEnabled: (filterId: number, enabled: boolean): UpdateEnabledAction => ({
    type: UPDATE_ENABLED,
    filterId,
    enabled
  }),
  updateTrackerSelection: (filterId: number): UpdateTrackerSelectionAction => ({
    type: UPDATE_TRACKER_SELECTION,
    filterId
  }),
  updateSearchCriteria: (
    searchCriteria: string
  ): UpdateSearchCriteriaAction => ({
    type: UPDATE_SEARCH_CRITERIA,
    searchCriteria
  }),
  changePage: (pageOffset: number): ChangePageAction => ({
    type: CHANGE_PAGE,
    pageOffset
  }),
  updatePageSize: (pageSize: number): UpdatePageSizeAction => ({
    type: UPDATE_PAGE_SIZE,
    pageSize
  }),
  resetPaging: (): ResetPagingAction => ({ type: RESET_PAGING }),
  pageRight: (): PageRightAction => ({ type: PAGE_RIGHT }),
  pageLeft: (): PageLeftAction => ({ type: PAGE_LEFT }),
  selectFirst: (): SelectFirstAction => ({ type: SELECT_FIRST }),
  selectLast: (): SelectLastAction => ({ type: SELECT_LAST }),
  selectNone: (): SelectNoneAction => ({ type: SELECT_NONE })
};
export interface StoreState {
  trackers: Array<StreamTaskType>;
  isLoading: boolean;
  showCompleted: boolean;
  sortBy?: SortByOptions;
  sortDirection: Directions;
  pageSize: number;
  pageOffset: number;
  searchCriteria: string;
  totalTrackers: number;
  numberOfPages: number;
  selectedTrackerId?: number;
}

export const defaultState: StoreState = {
  trackers: [],
  isLoading: false,
  showCompleted: false,
  sortBy: SortByOptions.pipelineUuid,
  sortDirection: Directions.ascending,
  pageSize: 10,
  pageOffset: 0,
  searchCriteria: "is:incomplete ",
  totalTrackers: 0,
  numberOfPages: 1,
  selectedTrackerId: undefined
};

export const reducer = prepareReducer(defaultState)
  .handleAction<AddTrackersAction>(
    ADD_TRACKERS,
    (state = defaultState, { streamTasks, totalStreamTasks }) => ({
      ...state,
      trackers: state.trackers.concat(streamTasks),
      totalTrackers: totalStreamTasks,
      numberOfPages: Math.ceil(totalStreamTasks / state.pageSize)
    })
  )
  .handleAction<UpdateSortAction>(
    UPDATE_SORT,
    (state = defaultState, { sortBy, sortDirection }) => ({
      ...state,
      sortBy: sortBy,
      sortDirection: sortDirection
    })
  )
  .handleAction<UpdateTrackersAction>(
    UPDATE_TRACKERS,
    (state = defaultState, { streamTasks, totalStreamTasks }) => ({
      ...state,
      trackers: streamTasks,
      totalTrackers: totalStreamTasks,
      numberOfPages: Math.ceil(totalStreamTasks / state.pageSize)
    })
  )
  .handleAction<UpdateEnabledAction>(
    UPDATE_ENABLED,
    (state = defaultState, { filterId, enabled }) => ({
      ...state,
      // TODO: use a filter then a map
      trackers: state.trackers.map(
        (tracker, i) =>
          tracker.filterId === filterId
            ? { ...tracker, enabled: enabled }
            : tracker
      )
    })
  )
  .handleAction<UpdateTrackerSelectionAction>(
    UPDATE_TRACKER_SELECTION,
    (state = defaultState, { filterId }) => ({
      ...state,
      selectedTrackerId: filterId
    })
  )
  .handleAction<MoveSelectionAction>(
    MOVE_SELECTION,
    (state = defaultState, { direction }) => {
      const currentIndex = state.trackers.findIndex(
        tracker => tracker.filterId === state.selectedTrackerId
      );

      let nextSelectedId;
      if (currentIndex === -1) {
        // There's no selection so we'll leave the selection as undefined
      } else if (direction.toLowerCase() === "up") {
        // TODO...erm its ascending or descending??
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
        selectedTrackerId: nextSelectedId
      };
    }
  )
  .handleAction<UpdateSearchCriteriaAction>(
    UPDATE_SEARCH_CRITERIA,
    (state = defaultState, { searchCriteria }) => {
      let sortBy = state.sortBy;
      if (searchCriteria.includes("sort:next")) {
        sortBy = undefined;
      }
      return {
        ...state,
        searchCriteria,
        sortBy
      };
    }
  )
  .handleAction<ChangePageAction>(
    CHANGE_PAGE,
    (state = defaultState, { pageOffset }) => ({
      ...state,
      pageOffset
    })
  )
  .handleAction<UpdatePageSizeAction>(
    UPDATE_PAGE_SIZE,
    (state = defaultState, { pageSize }) => ({
      ...state,
      pageSize
    })
  )
  .handleAction<ResetPagingAction>(RESET_PAGING, (state = defaultState) => ({
    ...state,
    pageOffset: defaultState.pageOffset
  }))
  .handleAction<PageRightAction>(PAGE_RIGHT, (state = defaultState) => {
    // We don't want to page further than is possible
    const currentPageOffset = state.pageOffset;
    const numberOfPages = state.numberOfPages - 1;
    const newPageOffset =
      currentPageOffset < numberOfPages ? currentPageOffset + 1 : numberOfPages;
    return {
      ...state,
      pageOffset: newPageOffset
    };
  })
  .handleAction<PageLeftAction>(PAGE_LEFT, (state = defaultState) => {
    // We don't want to page further than is possible
    const currentPageOffset = state.pageOffset;
    const newPageOffset = currentPageOffset > 0 ? currentPageOffset - 1 : 0;
    return {
      ...state,
      pageOffset: newPageOffset
    };
  })
  .handleAction<SelectFirstAction>(SELECT_FIRST, (state = defaultState) => ({
    ...state,
    selectedTrackerId: state.trackers[0].filterId
  }))
  .handleAction<SelectLastAction>(SELECT_LAST, (state = defaultState) => ({
    ...state,
    selectedTrackerId: state.trackers[state.trackers.length - 1].filterId
  }))
  .handleAction<SelectNoneAction>(SELECT_NONE, (state = defaultState) => ({
    ...state,
    selectedTrackerId: undefined
  }))
  .getReducer();
