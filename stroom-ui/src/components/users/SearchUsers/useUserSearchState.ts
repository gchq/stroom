import * as React from "react";
import { Account } from "../types";
import { ResultPage } from "../api/types";

interface UserSearchStateApi {
  users: ResultPage<Account>;
  setUsers: (users: ResultPage<Account>) => void;
  selectedUser: string;
  setSelectedUser: (userId: string) => void;
}

interface UserSearchState {
  users: ResultPage<Account>;
  selectedUser: string;
}

interface SetUsersAction {
  type: "set_user";
  users: ResultPage<Account>;
}

interface ChangeSelectedUserAction {
  type: "change_selected_user";
  userId: string;
}

const reducer = (
  state: UserSearchState,
  action: SetUsersAction | ChangeSelectedUserAction,
) => {
  switch (action.type) {
    case "set_user":
      return { ...state, users: action.users };
    case "change_selected_user":
      return { ...state, selectedUser: action.userId };
    default:
      return state;
  }
};

const useUserSearchState = (): UserSearchStateApi => {
  const [userState, dispatch] = React.useReducer(reducer, {
    users: {
      values: [],
      pageResponse: {
        offset: 0,
        length: 0,
        total: undefined,
        exact: false,
      },
    },
    selectedUser: "",
  });
  const setUsers = React.useCallback(
    (users: ResultPage<Account>) => dispatch({ type: "set_user", users }),
    [dispatch],
  );
  const setSelectedUser = React.useCallback(
    (userId: string) => dispatch({ type: "change_selected_user", userId }),
    [dispatch],
  );

  return {
    users: userState.users,
    selectedUser: userState.selectedUser,
    setUsers,
    setSelectedUser,
  };
};

export { useUserSearchState };
