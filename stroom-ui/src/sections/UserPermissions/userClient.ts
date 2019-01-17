import { Dispatch } from "redux";
import { GlobalStoreState } from "src/startup/reducers";

import { actionCreators } from "./redux";
import { wrappedGet } from "../../lib/fetchTracker.redux";
import { User } from "src/types";

const {
  usersReceived,
  usersInGroupReceived,
  groupsForUserReceived
} = actionCreators;

export const findUsers = (
  listId: string,
  name?: string,
  isGroup?: Boolean,
  uuid?: string
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();

  var url = new URL(`${state.config.values.stroomBaseServiceUrl}/users/v1`);
  if (!!name && name.length > 0) url.searchParams.append("name", name);
  if (!!isGroup) url.searchParams.append("isGroup", isGroup.toString());
  if (!!uuid && uuid.length > 0) url.searchParams.append("uuid", uuid);

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
