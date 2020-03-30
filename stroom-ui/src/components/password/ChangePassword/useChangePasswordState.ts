import { useReducer } from "react";

interface PasswordState {
  showChangeConfirmation: boolean;
  errorMessages: string[];
}

interface SetShowChangeConfirmationAction {
  type: "set_show_change_confirmation";
  showChangeConfirmation: boolean;
}

interface AddErrorMessageAction {
  type: "add_error_message";
  errorMessage: string;
}

const reducer = (
  state: PasswordState,
  action: SetShowChangeConfirmationAction | AddErrorMessageAction,
) => {
  switch (action.type) {
    case "set_show_change_confirmation":
      return {
        ...state,
        showChangeConfirmation: action.showChangeConfirmation,
      };
    case "add_error_message":
      return {
        ...state,
        errorMessages: state.errorMessages.concat(action.errorMessage),
      };
    default:
      return state;
  }
};

const useChangePasswordState = (): {
  showChangeConfirmation: boolean;
  errorMessages: string[];
  setShowChangeConfirmation: (showChangeConfirmation: boolean) => void;
  addErrorMessage: (errorMessage: string) => void;
} => {
  const [state, dispatch] = useReducer(reducer, {
    showChangeConfirmation: false,
    errorMessages: [],
  });
  return {
    showChangeConfirmation: state.showChangeConfirmation,
    errorMessages: state.errorMessages,
    setShowChangeConfirmation: (showChangeConfirmation: boolean) =>
      dispatch({
        type: "set_show_change_confirmation",
        showChangeConfirmation,
      }),
    addErrorMessage: (errorMessage: string) =>
      dispatch({ type: "add_error_message", errorMessage }),
  };
};

export default useChangePasswordState;
