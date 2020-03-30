import { useCallback } from "react";
import { ChangePasswordRequest } from "components/authentication/types";
import useApi from "components/authentication";
import usePasswordState from "./useChangePasswordState";

const useChangePassword = (): {
  errorMessages: string[];
  showChangeConfirmation: boolean;
  changePassword: (changePasswordRequest: ChangePasswordRequest) => void;
} => {
  const {
    addErrorMessage,
    errorMessages,
    showChangeConfirmation,
    setShowChangeConfirmation,
  } = usePasswordState();
  const { changePassword: changePasswordUsingApi } = useApi();
  const changePassword = useCallback(
    (changePasswordRequest: ChangePasswordRequest) => {
      changePasswordUsingApi(changePasswordRequest).then(response => {
        if (response.changeSucceeded) {
          // If we successfully changed the password then we want to redirect if there's a redirection URL
          if (
            changePasswordRequest.redirectUrl !== undefined &&
            changePasswordRequest.redirectUrl !== ""
          ) {
            window.location.href = changePasswordRequest.redirectUrl;
          } else {
            setShowChangeConfirmation(true);
          }
        } else {
          if (response.failedOn.includes("BAD_OLD_PASSWORD")) {
            addErrorMessage("Your old password is not correct");
          }
          if (response.failedOn.includes("COMPLEXITY")) {
            addErrorMessage(
              "Your new password does not meet the complexity requirements",
            );
          }
          if (response.failedOn.includes("REUSE")) {
            addErrorMessage("You may not reuse your previous password");
          }
          if (response.failedOn.includes("LENGTH")) {
            addErrorMessage("Your new password is too short");
          }
        }
      });
    },
    [changePasswordUsingApi, setShowChangeConfirmation, addErrorMessage],
  );

  return { changePassword, errorMessages, showChangeConfirmation };
};

export default useChangePassword;
