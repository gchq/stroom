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

import {
  prepareReducerById,
  ActionId,
  StateById
} from "../../lib/redux-actions-ts";
import { LineType } from "./types";

import { deleteItemFromObject } from "../../lib/treeUtils";

const LINE_CONTAINER_CREATED = "LINE_CONTAINER_CREATED";
const LINE_CONTAINER_DESTROYED = "LINE_CONTAINER_DESTROYED";
const LINE_CREATED = "LINE_CREATED";
const LINE_DESTROYED = "LINE_DESTROYED";

export interface StoreStatePerId {
  [s: string]: LineType;
}
export type StoreState = StateById<StoreStatePerId>;

export interface LineContainerCreatedAction
  extends ActionId,
    Action<"LINE_CONTAINER_CREATED"> {}
export interface LineContainerDestroyedAction
  extends ActionId,
    Action<"LINE_CONTAINER_DESTROYED"> {}
export interface LineCreatedAction
  extends ActionId,
    Action<"LINE_CREATED">,
    LineType {
  lineId: string;
}
export interface LineDestroyedAction
  extends ActionId,
    Action<"LINE_DESTROYED"> {
  lineId: string;
}

const actionCreators = {
  lineContainerCreated: (id: string): LineContainerCreatedAction => ({
    type: LINE_CONTAINER_CREATED,
    id
  }),
  lineContainerDestroyed: (id: string): LineContainerDestroyedAction => ({
    type: LINE_CONTAINER_DESTROYED,
    id
  }),
  lineCreated: (
    id: string,
    lineId: string,
    fromId: string,
    toId: string,
    lineType?: string
  ): LineCreatedAction => ({
    type: LINE_CREATED,
    id,
    lineId,
    lineType,
    fromId,
    toId
  }),
  lineDestroyed: (id: string, lineId: string): LineDestroyedAction => ({
    type: LINE_DESTROYED,
    id,
    lineId
  })
};

// State will be an object
const defaultStatePerId: StoreStatePerId = {};

const reducer = prepareReducerById(defaultStatePerId)
  .handleAction(LINE_CONTAINER_CREATED, (state, action) => defaultStatePerId)
  .handleAction<LineContainerDestroyedAction>(
    LINE_CONTAINER_DESTROYED,
    (state, action) => ({})
  )
  .handleAction<LineCreatedAction>(
    LINE_CREATED,
    (state, { lineId, lineType, fromId, toId }) => ({
      ...state,
      [lineId]: {
        lineType,
        fromId,
        toId
      }
    })
  )
  .handleAction<LineDestroyedAction>(LINE_DESTROYED, (state, { lineId }) =>
    deleteItemFromObject(state!, lineId)
  )
  .getReducer();

export { reducer, actionCreators };
