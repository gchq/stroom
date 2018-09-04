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

const actionCreators = createActions({
  ADD: (
    dataViewerId,
    streamAttributeMaps,
    total,
    pageSize,
    pageOffset,
  ) => ({
    dataViewerId,
    streamAttributeMaps,
    total,
    pageSize,
    pageOffset,
  }),
  UPDATE_STREAM_ATTRIBUTE_MAPS: (
    dataViewerId,
    streamAttributeMaps,
    total,
    pageSize,
    pageOffset,
  ) => ({
    dataViewerId,
    streamAttributeMaps,
    total,
    pageSize,
    pageOffset,
  }),
  SELECT_ROW: (dataViewerId, rowIndex) => ({ dataViewerId, rowIndex }),
  DESELECT_ROW: (dataViewerId, rowIndex) => ({ dataViewerId }),
  UPDATE_DATA_FOR_SELECTED_ROW: (dataViewerId, data) => ({ dataViewerId, data }),
  UPDATE_DETAILS_FOR_SELECTED_ROW: (dataViewerId, details) => ({ dataViewerId, details }),
  UPDATE_DATA_SOURCE: (dataViewerId, dataSource) => ({ dataViewerId, dataSource }),
});

const defaultState = {};

const reducer = handleActions(
  {
    ADD: (
      state,
      {
        payload: {
          dataViewerId, streamAttributeMaps, total, pageSize, pageOffset,
        },
      },
    ) => ({
      ...state,
      [dataViewerId]: {
        ...state[dataViewerId],
        total,
        streamAttributeMaps: state[dataViewerId].streamAttributeMaps.concat(streamAttributeMaps),
        pageSize,
        pageOffset,
      },
    }),
    UPDATE_STREAM_ATTRIBUTE_MAPS: (
      state,
      {
        payload: {
          dataViewerId, streamAttributeMaps, total, pageSize, pageOffset,
        },
      },
    ) => ({
      ...state,
      [dataViewerId]: {
        ...state[dataViewerId],
        total,
        streamAttributeMaps,
        pageSize,
        pageOffset,
      },
    }),
    SELECT_ROW: (state, { payload: { dataViewerId, rowIndex } }) => ({
      ...state,
      [dataViewerId]: {
        ...state[dataViewerId],
        selectedRow: rowIndex,
      },
    }),
    DESELECT_ROW: (state, { payload: { dataViewerId } }) => ({
      ...state,
      [dataViewerId]: {
        ...state[dataViewerId],
        selectedRow: undefined,
      },
    }),
    UPDATE_DATA_FOR_SELECTED_ROW: (state, { payload: { dataViewerId, data } }) => ({
      ...state,
      [dataViewerId]: {
        ...state[dataViewerId],
        dataForSelectedRow: data,
      },
    }),
    UPDATE_DETAILS_FOR_SELECTED_ROW: (state, { payload: { dataViewerId, details } }) => ({
      ...state,
      [dataViewerId]: {
        ...state[dataViewerId],
        detailsForSelectedRow: details,
      },
    }),
    UPDATE_DATA_SOURCE: (state, { payload: { dataViewerId, dataSource } }) => ({
      ...state,
      [dataViewerId]: {
        ...state[dataViewerId],
        dataSource,
      },
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
