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
import { Action, ActionCreator } from "redux";

import {
  prepareReducerById,
  ActionId,
  StateById
} from "../../lib/redux-actions-ts";
import { DataRow, AbstractFetchDataResult, DataSourceType } from "../../types";

export const ADD = "ADD";
export const UPDATE_STREAM_ATTRIBUTE_MAPS = "UPDATE_STREAM_ATTRIBUTE_MAPS";
export const SELECT_ROW = "SELECT_ROW";
export const DESELECT_ROW = "DESELECT_ROW";
export const UPDATE_DATA_FOR_SELECTED_ROW = "UPDATE_DATA_FOR_SELECTED_ROW";
export const UPDATE_DETAILS_FOR_SELECTED_ROW =
  "UPDATE_DETAILS_FOR_SELECTED_ROW";
export const UPDATE_DATA_SOURCE = "UPDATE_DATA_SOURCE";

export interface StreamAttributesMapAction extends ActionId {
  streamAttributeMaps: Array<DataRow>;
  total: number;
  pageSize: number;
  pageOffset: number;
}

export interface AddAction extends Action<"ADD">, StreamAttributesMapAction {}

export interface UpdateStreamAttributeMapsAction
  extends Action<"UPDATE_STREAM_ATTRIBUTE_MAPS">,
    StreamAttributesMapAction {}

export interface RowAction extends ActionId {
  selectedRow?: number;
}

export interface SelectRowAction extends Action<"SELECT_ROW">, RowAction {}
export interface DeselectRowAction extends Action<"DESELECT_ROW">, RowAction {}

export interface UpdateDataForSelectedRowAction
  extends Action<"UPDATE_DATA_FOR_SELECTED_ROW">,
    ActionId {
  dataForSelectedRow: AbstractFetchDataResult;
}
export interface UpdateDetailsForSelectedRowAction
  extends Action<"UPDATE_DETAILS_FOR_SELECTED_ROW">,
    ActionId {
  detailsForSelectedRow: DataRow;
}
export interface UpdateDataSourceAction
  extends Action<"UPDATE_DATA_SOURCE">,
    ActionId {
  dataSource: DataSourceType;
}

export interface ActionCreators {
  add: ActionCreator<AddAction>;
  updateStreamAttributeMaps: ActionCreator<UpdateStreamAttributeMapsAction>;
  selectRow: ActionCreator<SelectRowAction>;
  deselectRow: ActionCreator<DeselectRowAction>;
  updateDataForSelectedRow: ActionCreator<UpdateDataForSelectedRowAction>;
  updateDetailsForSelectedRow: ActionCreator<UpdateDetailsForSelectedRowAction>;
  updateDataSource: ActionCreator<UpdateDataSourceAction>;
}

export const actionCreators: ActionCreators = {
  add: (id, streamAttributeMaps, total, pageSize, pageOffset) => ({
    type: ADD,
    id,
    streamAttributeMaps,
    total,
    pageSize,
    pageOffset
  }),
  updateStreamAttributeMaps: (
    id,
    streamAttributeMaps,
    total,
    pageSize,
    pageOffset
  ) => ({
    type: UPDATE_STREAM_ATTRIBUTE_MAPS,
    id,
    streamAttributeMaps,
    total,
    pageSize,
    pageOffset
  }),
  selectRow: (id, selectedRow) => ({
    type: SELECT_ROW,
    id,
    selectedRow
  }),
  deselectRow: id => ({
    type: DESELECT_ROW,
    id,
    selectedRow: undefined
  }),
  updateDataForSelectedRow: (id, dataForSelectedRow) => ({
    type: UPDATE_DATA_FOR_SELECTED_ROW,
    id,
    dataForSelectedRow
  }),
  updateDetailsForSelectedRow: (id, detailsForSelectedRow) => ({
    type: UPDATE_DETAILS_FOR_SELECTED_ROW,
    id,
    detailsForSelectedRow
  }),
  updateDataSource: (id, dataSource) => ({
    type: UPDATE_DATA_SOURCE,
    id,
    dataSource
  })
};

export interface StoreStatePerId {
  streamAttributeMaps?: Array<DataRow>;
  total?: number;
  pageSize?: number;
  pageOffset?: number;
  selectedRow?: number;
  dataForSelectedRow?: AbstractFetchDataResult;
  detailsForSelectedRow?: DataRow;
  dataSource?: DataSourceType;
}

export interface StoreState extends StateById<StoreStatePerId> {}

export const defaultStatePerId: StoreStatePerId = {};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleActions<StreamAttributesMapAction>(
    [ADD, UPDATE_STREAM_ATTRIBUTE_MAPS],
    (
      state = defaultStatePerId,
      { streamAttributeMaps, total, pageSize, pageOffset }
    ) => ({ ...state, streamAttributeMaps, total, pageSize, pageOffset })
  )
  .handleActions<RowAction>(
    [SELECT_ROW, DESELECT_ROW],
    (state = defaultStatePerId, { selectedRow }) => ({
      ...state,
      selectedRow
    })
  )
  .handleAction<UpdateDataForSelectedRowAction>(
    UPDATE_DATA_FOR_SELECTED_ROW,
    (state = defaultStatePerId, { dataForSelectedRow }) => ({
      ...state,
      dataForSelectedRow
    })
  )
  .handleAction<UpdateDetailsForSelectedRowAction>(
    UPDATE_DETAILS_FOR_SELECTED_ROW,
    (state = defaultStatePerId, { detailsForSelectedRow }) => ({
      ...state,
      detailsForSelectedRow
    })
  )
  .handleAction<UpdateDataSourceAction>(
    UPDATE_DATA_SOURCE,
    (state = defaultStatePerId, { dataSource }) => ({
      ...state,
      dataSource
    })
  )
  .getReducer();
