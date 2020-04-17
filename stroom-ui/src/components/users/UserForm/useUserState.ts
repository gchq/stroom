import { useReducer } from "react";
import { Account } from "../types";

interface AccountStateApi {
  account?: Account;
  isCreating: boolean;
  setAccount: (account: Account) => void;
  clearAccount: () => void;
  setIsCreating: (isCreating: boolean) => void;
}

interface AccountState {
  accountBeingEdited?: Account;
  isCreating: boolean;
}

interface SetAccountAction {
  type: "set_account";
  account?: Account;
}

interface ClearAccountAction {
  type: "clear_account";
}

interface SetIsCreatingAction {
  type: "set_is_creating";
  isCreating: boolean;
}

const reducer = (
  state: AccountState,
  action: SetAccountAction | ClearAccountAction | SetIsCreatingAction,
) => {
  switch (action.type) {
    case "set_account":
      return { ...state, accountBeingEdited: action.account };
    case "set_is_creating":
      return { ...state, isCreating: action.isCreating };
    case "clear_account":
      return { ...state, accountBeingEdited: undefined };
    default:
      return state;
  }
};

const useUserState = (): AccountStateApi => {
  const [accountState, dispatch] = useReducer(reducer, {
    accountBeingEdited: undefined,
    isCreating: true,
  });
  return {
    account: accountState.accountBeingEdited,
    isCreating: accountState.isCreating,
    setAccount: (account: Account | undefined) => dispatch({ type: "set_account", account: account }),
    clearAccount: () => dispatch({ type: "clear_account" }),
    setIsCreating: (isCreating: boolean) =>
      dispatch({ type: "set_is_creating", isCreating }),
  };
};

export default useUserState;
