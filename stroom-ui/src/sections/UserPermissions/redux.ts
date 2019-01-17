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
import { User } from "../../types";
import { mapObject } from "../../lib/treeUtils";

const USERS_RECEIVED = "USERS_RECEIVED";
const USERS_IN_GROUP_RECEIVED = "USERS_IN_GROUP_RECEIVED";
const GROUPS_FOR_USER_RECEIEVED = "GROUPS_FOR_USER_RECEIEVED";
const USER_CREATED = "USER_CREATED";

export interface UsersReceivedAction extends Action<"USERS_RECEIVED"> {
  listId: string;
  users: Array<User>;
}

export interface UsersInGroupReceivedAction
  extends Action<"USERS_IN_GROUP_RECEIVED"> {
  groupUuid: string;
  users: Array<User>;
}

export interface GroupsForUserReceivedAction
  extends Action<"GROUPS_FOR_USER_RECEIEVED"> {
  userUuid: string;
  groups: Array<User>;
}

export interface UserCreatedAction extends Action<"USER_CREATED"> {
  user: User;
}

export const actionCreators = {
  usersReceived: (listId: string, users: Array<User>): UsersReceivedAction => ({
    type: USERS_RECEIVED,
    listId,
    users
  }),
  usersInGroupReceived: (
    groupUuid: string,
    users: Array<User>
  ): UsersInGroupReceivedAction => ({
    type: USERS_IN_GROUP_RECEIVED,
    groupUuid,
    users
  }),
  groupsForUserReceived: (
    userUuid: string,
    groups: Array<User>
  ): GroupsForUserReceivedAction => ({
    type: GROUPS_FOR_USER_RECEIEVED,
    userUuid,
    groups
  }),
  userCreated: (user: User): UserCreatedAction => ({
    type: USER_CREATED,
    user
  })
};

export interface StoreState {
  users: {
    [listId: string]: Array<User>;
  };
  usersInGroup: {
    [s: string]: Array<User>;
  };
  groupsForUser: {
    [s: string]: Array<User>;
  };
}

export const defaultState: StoreState = {
  users: {},
  usersInGroup: {},
  groupsForUser: {}
};

export const reducer = prepareReducer(defaultState)
  .handleAction<UsersReceivedAction>(
    USERS_RECEIVED,
    (state: StoreState, { listId, users }) => ({
      ...state,
      users: {
        ...state.users,
        [listId]: users
      }
    })
  )
  .handleAction<UsersInGroupReceivedAction>(
    USERS_IN_GROUP_RECEIVED,
    (state: StoreState, { groupUuid, users }) => ({
      ...state,
      usersInGroup: {
        ...state.usersInGroup,
        [groupUuid]: users
      }
    })
  )
  .handleAction<GroupsForUserReceivedAction>(
    GROUPS_FOR_USER_RECEIEVED,
    (state: StoreState, { userUuid, groups }) => ({
      ...state,
      groupsForUser: {
        ...state.groupsForUser,
        [userUuid]: groups
      }
    })
  )
  .handleAction<UserCreatedAction>(
    USER_CREATED,
    (state: StoreState, { user }) => ({
      ...state,
      users: mapObject(state.users, (u: Array<User>) => u.concat(user))
    })
  )
  .getReducer();
