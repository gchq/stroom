import { FormikHelpers } from "formik";
import * as queryString from "query-string";
import { useCallback } from "react";
import useHttpClient from "lib/useHttpClient";
import useRouter from "lib/useRouter";
import useConfig from "startup/config/useConfig";
import { ChangePasswordResponse } from "./types";
import {
  ChangePasswordRequest,
  Credentials,
  LoginResponse,
  PasswordValidationRequest,
  PasswordValidationResponse,
  ResetPasswordRequest,
} from "./types";
import useServiceUrl from "startup/config/useServiceUrl";

interface Api {
  apiLogin: (credentials: Credentials) => Promise<LoginResponse>;
  resetPassword: (
    resetPasswordRequest: ResetPasswordRequest,
  ) => Promise<ChangePasswordResponse>;
  changePassword: (
    changePasswordRequest: ChangePasswordRequest,
  ) => Promise<ChangePasswordResponse>;
  submitPasswordChangeRequest: (
    formData: any,
    formikBag: FormikHelpers<any>,
  ) => Promise<void>;
  isPasswordValid: (
    passwordValidationRequest: PasswordValidationRequest,
  ) => Promise<PasswordValidationResponse>;
}

export const useApi = (): Api => {
  const { httpGetJson, httpPostJsonResponse } = useHttpClient();
  let { clientId } = useConfig();
  const { authenticationServiceUrl } = useServiceUrl();

  // If we have a clientId on the URL we'll use that. It means we're logging
  // in on behalf of a relying party so we need to identify as them.
  const { router } = useRouter();
  if (!!router && !!router.location) {
    const query = queryString.parse(router.location.search);
    if (!!query.clientId) {
      clientId = query.clientId + "";
    }
  }

  const apiLogin = useCallback(
    (credentials: Credentials) => {
      const { email, password } = credentials;
      const loginServiceUrl = `${authenticationServiceUrl}/authenticate`;

      return httpPostJsonResponse(
        loginServiceUrl,
        {
          // This option means we send the cookies along with the request,
          // which means the auth service gets the sessionId.
          credentials: "include",
          body: JSON.stringify({
            email,
            password,
            requestingClientId: clientId,
          }),
        },
        false,
      );
    },
    [authenticationServiceUrl, clientId, httpPostJsonResponse],
  );

  const changePassword = useCallback(
    ({ password, oldPassword, email }: ChangePasswordRequest) =>
      httpPostJsonResponse(
        `${authenticationServiceUrl}/changePassword/`,
        { body: JSON.stringify({ newPassword: password, oldPassword, email }) },
        false,
      ),
    [authenticationServiceUrl, httpPostJsonResponse],
  );

  const resetPassword = useCallback(
    ({ password }: ResetPasswordRequest) =>
      httpPostJsonResponse(`${authenticationServiceUrl}/resetPassword/`, {
        body: JSON.stringify({ password }),
      }),
    [authenticationServiceUrl, httpPostJsonResponse],
  );

  const submitPasswordChangeRequest = useCallback(
    (formData: any) =>
      httpGetJson(
        `${authenticationServiceUrl}/reset/${formData.email}`,
        {},
        false,
      ),
    [authenticationServiceUrl, httpGetJson],
  );

  const isPasswordValid = useCallback(
    (passwordValidationRequest: PasswordValidationRequest) =>
      httpPostJsonResponse(`${authenticationServiceUrl}/isPasswordValid`, {
        body: JSON.stringify(passwordValidationRequest),
      }),
    [authenticationServiceUrl, httpPostJsonResponse],
  );

  return {
    apiLogin,
    submitPasswordChangeRequest,
    resetPassword,
    changePassword,
    isPasswordValid,
  };
};

export default useApi;
