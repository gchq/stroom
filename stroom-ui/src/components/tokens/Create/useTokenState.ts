import { useCallback, useReducer } from "react";
import { Token } from "../api/types";

interface TokenState {
  token: Token;
}

interface TokenStateApi {
  token: Token;
  setEnabled: (isEnabled: boolean) => void;
  setToken: (token: Token) => void;
}

interface SetEnabledAction {
  type: "enabled";
  enabled: boolean;
}
interface SetTokenAction {
  type: "token";
  token: Token;
}

const reducer = (
  state: TokenState,
  action: SetTokenAction | SetEnabledAction,
) => {
  switch (action.type) {
    case "enabled":
      return { ...state, token: { ...state.token, enabled: action.enabled } };
    case "token":
      return { ...state, token: action.token };
    default:
      return state;
  }
};

const useTokenState = (): TokenStateApi => {
  const [state, dispatch] = useReducer(reducer, {
    token: null,
  });
  return {
    token: state.token,
    setEnabled: useCallback((enabled: boolean) => dispatch({ type: "enabled", enabled }), []),
    setToken: useCallback((token: Token) => dispatch({ type: "token", token }), []),
  };
};

export { useTokenState };
