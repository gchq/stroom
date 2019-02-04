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
import { IndexVolume } from "../../types";

const INDEX_VOLUMES_RECEIVED = "INDEX_VOLUMES_RECEIVED";
const INDEX_VOLUMES_IN_GROUP_RECEIVED = "INDEX_VOLUMES_IN_GROUP_RECEIVED";

export interface IndexVolumesReceivedAction
  extends Action<"INDEX_VOLUMES_RECEIVED"> {
  indexVolumes: Array<IndexVolume>;
}

export interface IndexVolumesInGroupReceivedAction
  extends Action<"INDEX_VOLUMES_IN_GROUP_RECEIVED"> {
  groupName: string;
  indexVolumes: Array<IndexVolume>;
}

export const actionCreators = {
  indexVolumesReceived: (
    indexVolumes: Array<IndexVolume>
  ): IndexVolumesReceivedAction => ({
    type: INDEX_VOLUMES_RECEIVED,
    indexVolumes
  }),
  indexVolumesInGroupReceived: (
    groupName: string,
    indexVolumes: Array<IndexVolume>
  ): IndexVolumesInGroupReceivedAction => ({
    type: INDEX_VOLUMES_IN_GROUP_RECEIVED,
    groupName,
    indexVolumes
  })
};

export interface StoreState {
  indexVolumes: Array<IndexVolume>;
  indexVolumesInGroup: {
    [groupName: string]: Array<IndexVolume>;
  };
}

export const defaultState: StoreState = {
  indexVolumes: [],
  indexVolumesInGroup: {}
};

export const reducer = prepareReducer(defaultState)
  .handleAction<IndexVolumesReceivedAction>(
    INDEX_VOLUMES_RECEIVED,
    (state: StoreState, { indexVolumes }) => ({
      ...state,
      indexVolumes
    })
  )
  .handleAction<IndexVolumesInGroupReceivedAction>(
    INDEX_VOLUMES_IN_GROUP_RECEIVED,
    (state: StoreState, { groupName, indexVolumes }) => ({
      ...state,
      indexVolumesInGroup: {
        ...state.indexVolumesInGroup,
        [groupName]: indexVolumes
      }
    })
  )
  .getReducer();
