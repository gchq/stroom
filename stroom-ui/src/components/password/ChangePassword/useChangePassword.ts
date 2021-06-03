import { useCallback, useState } from "react";
import usePasswordState from "./useChangePasswordState";
import { useStroomApi } from "lib/useStroomApi";
import { ChangePasswordRequest, ChangePasswordResponse } from "api/stroom";

const useChangePassword = (): {
  errorMessages: string[];
  showChangeConfirmation: boolean;
  changePassword: (changePasswordRequest: ChangePasswordRequest) => void;
  isSubmitting: boolean;
} => {
  const { errorMessages, showChangeConfirmation, setShowChangeConfirmation } =
    usePasswordState();
  const [isSubmitting, setSubmitting] = useState(false);

  const { exec } = useStroomApi();

  const changePassword = useCallback(
    (changePasswordRequest: ChangePasswordRequest) => {
      setSubmitting(true);
      exec(
        (api) => api.authentication.changePassword(changePasswordRequest),
        (response: ChangePasswordResponse) => {
          if (response.changeSucceeded) {
            // // If we successfully changed the password then we want to redirect if there's a redirection URL
            // if (
            //   changePasswordRequest.redirectUri !== undefined &&
            //   changePasswordRequest.redirectUri !== ""
            // ) {
            //   window.location.href = changePasswordRequest.redirectUri;
            // } else {
            setShowChangeConfirmation(true);
            // }
          } else {
            setSubmitting(false);
            // if (response.failedOn.includes("BAD_OLD_PASSWORD")) {
            //   addErrorMessage("Your old password is not correct");
            // }
            // if (response.failedOn.includes("COMPLEXITY")) {
            //   addErrorMessage(
            //     "Your new password does not meet the complexity requirements",
            //   );
            // }
            // if (response.failedOn.includes("REUSE")) {
            //   addErrorMessage("You may not reuse your previous password");
            // }
            // if (response.failedOn.includes("LENGTH")) {
            //   addErrorMessage("Your new password is too short");
            // }
          }
        },
      );
    },
    [exec, setShowChangeConfirmation],
  );

  return {
    changePassword,
    errorMessages,
    showChangeConfirmation,
    isSubmitting,
  };
};

export default useChangePassword;
