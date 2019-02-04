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
import { IndexVolumeGroup } from "../../types";
import { mapObject } from "../../lib/treeUtils";

const INDEX_VOLUME_GROUP_NAMES_RECEIVED = "INDEX_VOLUME_GROUP_NAMES_RECEIVED";
const INDEX_VOLUME_GROUPS_RECEIVED = "INDEX_VOLUME_GROUPS_RECEIVED";
const INDEX_VOLUME_GROUP_RECEIVED = "INDEX_VOLUME_GROUP_RECEIVED";
const INDEX_VOLUME_GROUP_CREATED = "INDEX_VOLUME_GROUP_CREATED";
const INDEX_VOLUME_GROUP_DELETED = "INDEX_VOLUME_GROUP_DELETED";

export interface IndexVolumeGroupNamesReceivedAction
  extends Action<"INDEX_VOLUME_GROUP_NAMES_RECEIVED"> {
  names: Array<string>;
}

export interface IndexVolumeGroupsReceivedAction
  extends Action<"INDEX_VOLUME_GROUPS_RECEIVED"> {
  indexVolumeGroups: Array<IndexVolumeGroup>;
}

export interface IndexVolumeGroupReceivedAction
  extends Action<"INDEX_VOLUME_GROUP_RECEIVED"> {
  indexVolumeGroup: IndexVolumeGroup;
}

export interface IndexVolumeGroupCreatedAction
  extends Action<"INDEX_VOLUME_GROUP_CREATED"> {
  indexVolumeGroup: IndexVolumeGroup;
}
export interface IndexVolumeGroupDeletedAction
  extends Action<"INDEX_VOLUME_GROUP_DELETED"> {
  name: string;
}

export const actionCreators = {
  indexVolumeGroupNamesReceived: (
    names: Array<string>
  ): IndexVolumeGroupNamesReceivedAction => ({
    type: INDEX_VOLUME_GROUP_NAMES_RECEIVED,
    names
  }),
  indexVolumeGroupsReceived: (
    indexVolumeGroups: Array<IndexVolumeGroup>
  ): IndexVolumeGroupsReceivedAction => ({
    type: INDEX_VOLUME_GROUPS_RECEIVED,
    indexVolumeGroups
  }),
  indexVolumeGroupReceived: (
    indexVolumeGroup: IndexVolumeGroup
  ): IndexVolumeGroupReceivedAction => ({
    type: INDEX_VOLUME_GROUP_RECEIVED,
    indexVolumeGroup
  }),
  indexVolumeGroupCreated: (
    indexVolumeGroup: IndexVolumeGroup
  ): IndexVolumeGroupCreatedAction => ({
    type: INDEX_VOLUME_GROUP_CREATED,
    indexVolumeGroup
  }),
  indexVolumeGroupDeleted: (name: string): IndexVolumeGroupDeletedAction => ({
    type: INDEX_VOLUME_GROUP_DELETED,
    name
  })
};

export interface StoreState {
  groupNames: Array<string>;
  groups: Array<IndexVolumeGroup>;
  groupsByName: {
    [groupName: string]: IndexVolumeGroup;
  };
}

export const defaultState: StoreState = {
  groupNames: [],
  groups: [],
  groupsByName: {}
};

export const reducer = prepareReducer(defaultState)
  .handleAction<IndexVolumeGroupNamesReceivedAction>(
    INDEX_VOLUME_GROUP_NAMES_RECEIVED,
    (state: StoreState, { names }) => ({
      groupNames: names,
      groups: state.groups.filter(g => names.includes(g.name)),
      groupsByName: mapObject<IndexVolumeGroup, IndexVolumeGroup>(
        state.groupsByName,
        (name, group) => (names.includes(name) ? group : undefined)
      )
    })
  )
  .handleAction<IndexVolumeGroupsReceivedAction>(
    INDEX_VOLUME_GROUPS_RECEIVED,
    (_: StoreState, { indexVolumeGroups }) => ({
      groupNames: indexVolumeGroups.map(i => i.name),
      groups: indexVolumeGroups,
      groupsByName: indexVolumeGroups.reduce(
        (acc, group) => ({
          ...acc,
          [group.name]: group
        }),
        {}
      )
    })
  )
  .handleAction<IndexVolumeGroupReceivedAction>(
    INDEX_VOLUME_GROUP_RECEIVED,
    (state: StoreState, { indexVolumeGroup }) => ({
      ...state,
      groups: [
        ...state.groups.filter(g => g.name !== indexVolumeGroup.name),
        indexVolumeGroup
      ],
      groupsByName: {
        ...state.groupsByName,
        [indexVolumeGroup.name]: indexVolumeGroup
      }
    })
  )
  .handleAction<IndexVolumeGroupCreatedAction>(
    INDEX_VOLUME_GROUP_CREATED,
    (state: StoreState, { indexVolumeGroup }) => ({
      groupNames: [...state.groupNames, indexVolumeGroup.name],
      groups: [...state.groups, indexVolumeGroup],
      groupsByName: {
        ...state.groupsByName,
        [indexVolumeGroup.name]: indexVolumeGroup
      }
    })
  )
  .handleAction<IndexVolumeGroupDeletedAction>(
    INDEX_VOLUME_GROUP_DELETED,
    (state: StoreState, { name }) => ({
      groupNames: state.groupNames.filter(n => n !== name),
      groups: state.groups.filter(g => g.name !== name),
      groupsByName: mapObject<IndexVolumeGroup, IndexVolumeGroup>(
        state.groupsByName,
        (gName, group) => (gName !== name ? group : undefined)
      )
    })
  )
  .getReducer();
