import { Dispatch } from "redux";
import { GlobalStoreState } from "src/startup/reducers";

import { actionCreators } from "./redux";
import { wrappedGet } from "../../lib/fetchTracker.redux";
import { User } from "src/types";

const { usersReceived } = actionCreators;

export const findUsers = (
  pickerId: string,
  name?: string,
  isGroup?: Boolean,
  id?: Number,
  uuid?: string
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();

  var url = new URL(`${state.config.values.stroomBaseServiceUrl}/users/v1`);
  if (!!name && name.length > 0) url.searchParams.append("name", name);
  if (!!isGroup) url.searchParams.append("isGroup", isGroup.toString());
  if (!!id) url.searchParams.append("id", id.toString());
  if (!!uuid && uuid.length > 0) url.searchParams.append("uuid", uuid);

  wrappedGet(
    dispatch,
    state,
    url.href,
    r =>
      r
        .json()
        .then((users: Array<User>) => dispatch(usersReceived(pickerId, users))),
    {},
    true
  );
};
