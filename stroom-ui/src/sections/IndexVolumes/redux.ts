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
import { mapObject } from "../../lib/treeUtils";

const INDEX_VOLUMES_RECEIVED = "INDEX_VOLUMES_RECEIVED";
const INDEX_VOLUMES_IN_GROUP_RECEIVED = "INDEX_VOLUMES_IN_GROUP_RECEIVED";
const INDEX_VOLUME_RECEIVED = "INDEX_VOLUME_RECEIVED";
const INDEX_VOLUME_CREATED = "INDEX_VOLUME_CREATED";
const INDEX_VOLUME_DELETED = "INDEX_VOLUME_DELETED";
const INDEX_VOLUME_ADDED_TO_GROUP = "INDEX_VOLUME_ADDED_TO_GROUP";
const INDEX_VOLUME_REMOVED_FROM_GROUP = "INDEX_VOLUME_REMOVED_FROM_GROUP";

export interface IndexVolumesReceivedAction
  extends Action<"INDEX_VOLUMES_RECEIVED"> {
  indexVolumes: Array<IndexVolume>;
}

export interface IndexVolumesInGroupReceivedAction
  extends Action<"INDEX_VOLUMES_IN_GROUP_RECEIVED"> {
  groupName: string;
  indexVolumes: Array<IndexVolume>;
}

export interface IndexVolumeReceivedAction
  extends Action<"INDEX_VOLUME_RECEIVED"> {
  indexVolume: IndexVolume;
}

export interface IndexVolumeCreatedAction
  extends Action<"INDEX_VOLUME_CREATED"> {
  indexVolume: IndexVolume;
}

export interface IndexVolumeDeletedAction
  extends Action<"INDEX_VOLUME_DELETED"> {
  indexVolumeId: number;
}

export interface IndexVolumeAddedToGroupAction
  extends Action<"INDEX_VOLUME_ADDED_TO_GROUP"> {
  indexVolumeId: number;
  groupName: string;
}

export interface IndexVolumeRemovedFromGroupAction
  extends Action<"INDEX_VOLUME_REMOVED_FROM_GROUP"> {
  indexVolumeId: number;
  groupName: string;
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
  }),
  indexVolumeReceived: (
    indexVolume: IndexVolume
  ): IndexVolumeReceivedAction => ({
    type: INDEX_VOLUME_RECEIVED,
    indexVolume
  }),
  indexVolumeCreated: (indexVolume: IndexVolume): IndexVolumeCreatedAction => ({
    type: INDEX_VOLUME_CREATED,
    indexVolume
  }),
  indexVolumeDeleted: (indexVolumeId: number): IndexVolumeDeletedAction => ({
    type: INDEX_VOLUME_DELETED,
    indexVolumeId
  }),
  indexVolumeAddedToGroup: (
    indexVolumeId: number,
    groupName: string
  ): IndexVolumeAddedToGroupAction => ({
    type: INDEX_VOLUME_ADDED_TO_GROUP,
    indexVolumeId,
    groupName
  }),
  indexVolumeRemovedFromGroup: (
    indexVolumeId: number,
    groupName: string
  ): IndexVolumeRemovedFromGroupAction => ({
    type: INDEX_VOLUME_REMOVED_FROM_GROUP,
    indexVolumeId,
    groupName
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
  .handleAction<IndexVolumeReceivedAction>(
    INDEX_VOLUME_RECEIVED,
    (state: StoreState, { indexVolume }) => ({
      ...state,
      indexVolumes: state.indexVolumes
        .filter(v => v.id !== indexVolume.id)
        .concat([indexVolume])
    })
  )
  .handleAction<IndexVolumeCreatedAction>(
    INDEX_VOLUME_CREATED,
    (state: StoreState, { indexVolume }) => ({
      ...state,
      indexVolumes: state.indexVolumes.concat([indexVolume])
    })
  )
  .handleAction<IndexVolumeDeletedAction>(
    INDEX_VOLUME_DELETED,
    (state: StoreState, { indexVolumeId }) => ({
      indexVolumes: state.indexVolumes.filter(v => v.id !== indexVolumeId),
      indexVolumesInGroup: mapObject(state.indexVolumesInGroup, (gName, vols) =>
        vols.filter(v => v.id !== indexVolumeId)
      )
    })
  )
  .handleAction<IndexVolumeAddedToGroupAction>(
    INDEX_VOLUME_ADDED_TO_GROUP,
    (state: StoreState, { indexVolumeId, groupName }) => ({
      ...state,
      indexVolumesInGroup: mapObject(
        state.indexVolumesInGroup,
        (gName, vols) => {
          if (gName === groupName) {
            let indexToAdd: IndexVolume | undefined = state.indexVolumes.find(
              v => v.id === indexVolumeId
            );
            if (indexToAdd) {
              return vols.concat([indexToAdd]);
            }
          }
          return vols;
        }
      )
    })
  )
  .handleAction<IndexVolumeRemovedFromGroupAction>(
    INDEX_VOLUME_REMOVED_FROM_GROUP,
    (state: StoreState, { indexVolumeId, groupName }) => ({
      ...state,
      indexVolumesInGroup: mapObject(
        state.indexVolumesInGroup,
        (gName, vols) => {
          if (gName === groupName) {
            return vols.filter(v => v.id !== indexVolumeId);
          }
          return vols;
        }
      )
    })
  )
  .getReducer();
