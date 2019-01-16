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

export interface UsersReceivedAction
  extends ActionId,
    Action<"USERS_RECEIVED"> {
  users: Array<User>;
}

export const actionCreators = {
  usersReceived: (id: string, users: Array<User>): UsersReceivedAction => ({
    type: USERS_RECEIVED,
    id,
    users
  })
};

export interface StoreStateById {
  users: Array<User>;
}

export type StoreState = StateById<StoreStateById>;

export const defaultStatePerId: StoreStateById = {
  users: []
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<UsersReceivedAction>(USERS_RECEIVED, (state, { users }) => ({
    ...state,
    users
  }))
  .getReducer();
