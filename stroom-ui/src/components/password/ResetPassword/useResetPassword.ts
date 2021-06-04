import { FormikBag } from "formik";
import { useCallback } from "react";
import { useRouter } from "lib/useRouter";
import * as queryString from "query-string";
import { useStroomApi } from "lib/useStroomApi";
import {
  ChangePasswordRequest,
  ChangePasswordResponse,
  ResetPasswordRequest,
} from "api/stroom";

const useResetPassword = (): {
  submitPasswordChangeRequest: (
    formData: any,
    formikBag: FormikBag<any, any>,
  ) => void;
  resetPassword: (resetPasswordRequest: ResetPasswordRequest) => void;
} => {
  const { history } = useRouter();
  const { exec } = useStroomApi();
  const submitPasswordChangeRequest = useCallback(() => {
    const request: ChangePasswordRequest = {
      userId: "sf",
      currentPassword: "sdf",
      newPassword: "szdf",
      confirmNewPassword: "\fe",
    };
    exec(
      (api) => api.authentication.changePassword(request),
      () => history.push("/s/confirmPasswordResetEmail"),
    );
  }, [history, exec]);

  const { router } = useRouter();
  const resetPassword = useCallback(
    (resetPasswordRequest: ResetPasswordRequest) => {
      exec(
        (api) => api.authentication.resetPassword(resetPasswordRequest),
        (response: ChangePasswordResponse) => {
          if (response.changeSucceeded) {
            let redirectUri: string;

            if (!!router && !!router.location) {
              const query = queryString.parse(router.location.search);
              if (query.redirect_uri) {
                redirectUri = query.redirect_uri + "";
              }
            }

            if (redirectUri !== undefined) {
              window.location.href = redirectUri;
            } else {
              console.error("No redirect URI available for redirect!");
            }
          } else {
            // const errorMessage = [];
            // if (response.failedOn.includes("COMPLEXITY")) {
            //   errorMessage.push(
            //     "Your new password does not meet the complexity requirements",
            //   );
            // }
            // if (response.failedOn.includes("LENGTH")) {
            //   errorMessage.push("Your new password is too short");
            // }
          }
        },
      );
    },
    [exec, router],
  );

  return { submitPasswordChangeRequest, resetPassword };
};

export default useResetPassword;
