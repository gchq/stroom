import { FormikHelpers } from "formik";
import * as queryString from "query-string";
import { useCallback } from "react";
import useHttpClient from "lib/useHttpClient";
import useRouter from "lib/useRouter";
import {
  ChangePasswordRequest,
  ChangePasswordResponse,
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
  const { httpGetEmptyResponse, httpPostJsonResponse } = useHttpClient();
  const { authenticationServiceUrl } = useServiceUrl();
  let redirectUri: string;

  const { router } = useRouter();
  if (!!router && !!router.location) {
    const query = queryString.parse(router.location.search);
    if (!!query.redirect_uri) {
      redirectUri = query.redirect_uri + "";
    }
  }

  const apiLogin = useCallback(
    (credentials: Credentials) => {
      const { email, password } = credentials;
      const loginServiceUrl = `${authenticationServiceUrl}/noauth/login?redirect_uri=${encodeURI(redirectUri)}`;

      return httpPostJsonResponse(
        loginServiceUrl,
        {
          // This option means we send the cookies along with the request,
          // which means the auth service gets the sessionId.
          credentials: "include",
          body: JSON.stringify({
            email,
            password,
          }),
        },
        true,
        false,
      );
    },
    [authenticationServiceUrl, redirectUri, httpPostJsonResponse],
  );

  const changePassword = useCallback(
    ({ email, oldPassword, newPassword }: ChangePasswordRequest) =>
      httpPostJsonResponse(
        `${authenticationServiceUrl}/noauth/changePassword/`,
        { body: JSON.stringify({ newPassword, oldPassword, email }) },
        true,
        false,
      ),
    [authenticationServiceUrl, httpPostJsonResponse],
  );

  const resetPassword = useCallback(
    ({ newPassword }: ResetPasswordRequest) =>
      httpPostJsonResponse(`${authenticationServiceUrl}/resetPassword/`, {
        body: JSON.stringify({ newPassword }),
      }),
    [authenticationServiceUrl, httpPostJsonResponse],
  );

  const submitPasswordChangeRequest = useCallback(
    (formData: any) =>
      httpGetEmptyResponse(
        `${authenticationServiceUrl}/reset/${formData.email}`,
        {},
        true,
        false,
      ),
    [authenticationServiceUrl, httpGetEmptyResponse],
  );

  const isPasswordValid = useCallback(
    (passwordValidationRequest: PasswordValidationRequest) =>
      httpPostJsonResponse(
        `${authenticationServiceUrl}/noauth/isPasswordValid`,
        {
          body: JSON.stringify(passwordValidationRequest),
        },
      ),
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
