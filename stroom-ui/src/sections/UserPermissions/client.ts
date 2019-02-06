import { Dispatch } from "redux";
import { GlobalStoreState } from "src/startup/reducers";

import { actionCreators } from "./redux";
import {
  wrappedGet,
  wrappedPost,
  wrappedPut,
  wrappedDelete
} from "../../lib/fetchTracker.redux";
import { User } from "src/types";

const {
  usersReceived,
  usersInGroupReceived,
  groupsForUserReceived,
  userCreated,
  userDeleted,
  userAddedToGroup,
  userRemovedFromGroup
} = actionCreators;

export const findUsers = (
  listId: string,
  name?: string,
  isGroup?: Boolean,
  uuid?: string
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();

  var url = new URL(`${state.config.values.stroomBaseServiceUrl}/users/v1`);
  if (name !== undefined && name.length > 0)
    url.searchParams.append("name", name);
  if (isGroup !== undefined)
    url.searchParams.append("isGroup", isGroup.toString());
  if (uuid !== undefined && uuid.length > 0)
    url.searchParams.append("uuid", uuid);

  wrappedGet(
    dispatch,
    state,
    url.href,
    r =>
      r
        .json()
        .then((users: Array<User>) => dispatch(usersReceived(listId, users))),
    {},
    true
  );
};

export const findUsersInGroup = (groupUuid: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = `${
    state.config.values.stroomBaseServiceUrl
  }/users/v1/usersInGroup/${groupUuid}`;

  wrappedGet(
    dispatch,
    state,
    url,
    r =>
      r
        .json()
        .then((users: Array<User>) =>
          dispatch(usersInGroupReceived(groupUuid, users))
        ),
    {},
    true
  );
};

export const findGroupsForUser = (userUuid: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = `${
    state.config.values.stroomBaseServiceUrl
  }/users/v1/groupsForUser/${userUuid}`;

  wrappedGet(
    dispatch,
    state,
    url,
    r =>
      r
        .json()
        .then((users: Array<User>) =>
          dispatch(groupsForUserReceived(userUuid, users))
        ),
    {},
    true
  );
};

export const createUser = (name: string, isGroup: boolean) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = `${state.config.values.stroomBaseServiceUrl}/users/v1`;

  // Create DTO
  const body = JSON.stringify({
    name,
    isGroup
  });

  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response.json().then((user: User) => dispatch(userCreated(user))),
    {
      body
    }
  );
};

export const deleteUser = (uuid: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = `${state.config.values.stroomBaseServiceUrl}/users/v1/${uuid}`;

  wrappedDelete(dispatch, state, url, response =>
    response.text().then(() => dispatch(userDeleted(uuid)))
  );
};

export const addUserToGroup = (userUuid: string, groupUuid: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = `${
    state.config.values.stroomBaseServiceUrl
  }/users/v1/${userUuid}/${groupUuid}`;

  wrappedPut(dispatch, state, url, response =>
    response.text().then(() => dispatch(userAddedToGroup(userUuid, groupUuid)))
  );
};

export const removeUserFromGroup = (userUuid: string, groupUuid: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = `${
    state.config.values.stroomBaseServiceUrl
  }/users/v1/${userUuid}/${groupUuid}`;

  wrappedDelete(dispatch, state, url, response =>
    response
      .text()
      .then(() => dispatch(userRemovedFromGroup(userUuid, groupUuid)))
  );
};
