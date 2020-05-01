import { FormikBag } from "formik";
import { useCallback } from "react";
import { ChangePasswordResponse, ResetPasswordRequest } from "components/authentication/types";
import useApi from "components/authentication";
import { useRouter } from "lib/useRouter";
import * as queryString from "query-string";

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
        history.push("/s/confirmPasswordResetEmail"),
      );
    },
    [history, submitPasswordChangeRequestUsingApi],
  );

  const { router } = useRouter();
  const resetPassword = useCallback(
    (resetPasswordRequest: ResetPasswordRequest) => {
      resetPasswordUsingApi(resetPasswordRequest).then(
        (response: ChangePasswordResponse) => {
          if (response.changeSucceeded) {

            let redirectUri: string;

            if (!!router && !!router.location) {
              const query = queryString.parse(router.location.search);
              if (!!query.redirect_uri) {
                redirectUri = query.redirect_uri + "";
              }
            }

            if (redirectUri !== undefined) {
              window.location.href = redirectUri;
            } else {
              console.error("No redirect URI available for redirect!");
            }

          } else {
            const errorMessage = [];
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
    [resetPasswordUsingApi, router],
  );

  return { submitPasswordChangeRequest, resetPassword };
};

export default useResetPassword;
