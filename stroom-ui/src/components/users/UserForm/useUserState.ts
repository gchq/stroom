import { useReducer } from "react";
import { User } from "../types";

interface UserStateApi {
  user?: User;
  isCreating: boolean;
  setUser: (user: User) => void;
  clearUser: () => void;
  setIsCreating: (isCreating: boolean) => void;
}

interface UsersState {
  userBeingEdited?: User;
  isCreating: boolean;
}

interface SetUserAction {
  type: "set_user";
  user?: User;
}

interface ClearUserAction {
  type: "clear_user";
}

interface SetIsCreatingAction {
  type: "set_is_creating";
  isCreating: boolean;
}

const reducer = (
  state: UsersState,
  action: SetUserAction | ClearUserAction | SetIsCreatingAction,
) => {
  switch (action.type) {
    case "set_user":
      return { ...state, userBeingEdited: action.user };
    case "set_is_creating":
      return { ...state, isCreating: action.isCreating };
    case "clear_user":
      return { ...state, userBeingEdited: undefined };
    default:
      return state;
  }
};

const useUserState = (): UserStateApi => {
  const [userState, dispatch] = useReducer(reducer, {
    userBeingEdited: undefined,
    isCreating: true,
  });
  return {
    user: userState.userBeingEdited,
    isCreating: userState.isCreating,
    setUser: (user: User | undefined) => dispatch({ type: "set_user", user }),
    clearUser: () => dispatch({ type: "clear_user" }),
    setIsCreating: (isCreating: boolean) =>
      dispatch({ type: "set_is_creating", isCreating }),
  };
};

export default useUserState;
