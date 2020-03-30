import { FormikBag } from "formik";
import { useCallback } from "react";
import {
  ChangePasswordResponse,
  ResetPasswordRequest,
} from "components/authentication/types";
import useApi from "components/authentication";
import { useRouter } from "lib/useRouter";
import useConfig from "startup/config/useConfig";

const useResetPassword = (): {
  submitPasswordChangeRequest: (
    formData: any,
    formikBag: FormikBag<any, any>,
  ) => void;
  resetPassword: (resetPasswordRequest: ResetPasswordRequest) => void;
} => {
  const { history } = useRouter();
  const {
    submitPasswordChangeRequest: submitPasswordChangeRequestUsingApi,
    resetPassword: resetPasswordUsingApi,
  } = useApi();
  const submitPasswordChangeRequest = useCallback(
    (formData: any, formikBag: FormikBag<any, any>) => {
      submitPasswordChangeRequestUsingApi(formData, formikBag).then(() =>
        history.push("/confirmPasswordResetEmail"),
      );
    },
    [history, submitPasswordChangeRequestUsingApi],
  );

  const { stroomUiUrl } = useConfig();
  const resetPassword = useCallback(
    (resetPasswordRequest: ResetPasswordRequest) => {
      resetPasswordUsingApi(resetPasswordRequest).then(
        (response: ChangePasswordResponse) => {
          if (response.changeSucceeded) {
            if (stroomUiUrl !== undefined) {
              window.location.href = stroomUiUrl;
            } else {
              console.error("No stroom UI url available for redirect!");
            }
          } else {
            let errorMessage = [];
            if (response.failedOn.includes("COMPLEXITY")) {
              errorMessage.push(
                "Your new password does not meet the complexity requirements",
              );
            }
            if (response.failedOn.includes("LENGTH")) {
              errorMessage.push("Your new password is too short");
            }
          }
        },
      );
    },
    [resetPasswordUsingApi, stroomUiUrl],
  );

  return { submitPasswordChangeRequest, resetPassword };
};

export default useResetPassword;
