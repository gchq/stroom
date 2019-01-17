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
  StateById,
  ActionId
} from "../../lib/redux-actions-ts";
import { User } from "src/types";

const USERS_RECEIVED = "USERS_RECEIVED";
const USERS_IN_GROUP_RECEIVED = "USERS_IN_GROUP_RECEIVED";
const GROUPS_FOR_USER_RECEIEVED = "GROUPS_FOR_USER_RECEIEVED";
const USER_CREATED = "USER_CREATED";

export interface UsersReceivedAction
  extends ActionId,
    Action<"USERS_RECEIVED"> {
  users: Array<User>;
}

export interface UsersInGroupReceivedAction
  extends ActionId,
    Action<"USERS_IN_GROUP_RECEIVED"> {
  groupUuid: string;
  users: Array<User>;
}

export interface GroupsForUserReceivedAction
  extends ActionId,
    Action<"GROUPS_FOR_USER_RECEIEVED"> {
  userUuid: string;
  groups: Array<User>;
}

export interface UserCreatedAction extends ActionId, Action<"USER_CREATED"> {
  user: User;
}

export const actionCreators = {
  usersReceived: (id: string, users: Array<User>): UsersReceivedAction => ({
    type: USERS_RECEIVED,
    id,
    users
  }),
  usersInGroupReceived: (
    id: string,
    groupUuid: string,
    users: Array<User>
  ): UsersInGroupReceivedAction => ({
    type: USERS_IN_GROUP_RECEIVED,
    id,
    groupUuid,
    users
  }),
  groupsForUserReceived: (
    id: string,
    userUuid: string,
    groups: Array<User>
  ): GroupsForUserReceivedAction => ({
    type: GROUPS_FOR_USER_RECEIEVED,
    id,
    userUuid,
    groups
  }),
  userCreated: (id: string, user: User): UserCreatedAction => ({
    type: USER_CREATED,
    id,
    user
  })
};

export interface StoreStateById {
  users: Array<User>;
  usersInGroup: {
    [s: string]: Array<User>;
  };
  groupsForUser: {
    [s: string]: Array<User>;
  };
}

export type StoreState = StateById<StoreStateById>;

export const defaultStatePerId: StoreStateById = {
  users: [],
  usersInGroup: {},
  groupsForUser: {}
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<UsersReceivedAction>(
    USERS_RECEIVED,
    (state: StoreStateById, { users }) => ({
      ...state,
      users
    })
  )
  .handleAction<UsersInGroupReceivedAction>(
    USERS_IN_GROUP_RECEIVED,
    (state: StoreStateById, { groupUuid, users }) => ({
      ...state,
      usersInGroup: {
        ...state.usersInGroup,
        [groupUuid]: users
      }
    })
  )
  .handleAction<GroupsForUserReceivedAction>(
    GROUPS_FOR_USER_RECEIEVED,
    (state: StoreStateById, { userUuid, groups }) => ({
      ...state,
      groupsForUser: {
        ...state.groupsForUser,
        [userUuid]: groups
      }
    })
  )
  .handleAction<UserCreatedAction>(
    USER_CREATED,
    (state: StoreStateById, { user }) => ({
      ...state,
      users: [...state.users, user]
    })
  )
  .getReducer();
