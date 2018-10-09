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

export const START_DEBUGGING = "START_DEBUGGING";

export interface StartDebuggingAction
  extends Action<"START_DEBUGGING">,
    ActionId {
  pipelineId: string;
}

export interface StoreStateById {
  pipelineId?: string;
}

export interface StoreState extends StateById<StoreStateById> {}

export const actionCreators = {
  startDebugging: (id: string, pipelineId: string): StartDebuggingAction => ({
    type: START_DEBUGGING,
    id,
    pipelineId
  })
};

export const defaultStatePerId: StoreStateById = {
  pipelineId: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<StartDebuggingAction>(
    START_DEBUGGING,
    (state, { pipelineId }) => ({
      pipelineId
    })
  )
  .getReducer();
