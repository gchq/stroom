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
import useUrlFactory from "lib/useUrlFactory";

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
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/authentication/v1");
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
      const loginServiceUrl = `${resource}/noauth/login?redirect_uri=${encodeURI(redirectUri)}`;

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
    [resource, redirectUri, httpPostJsonResponse],
  );

  const changePassword = useCallback(
    ({ email, oldPassword, newPassword }: ChangePasswordRequest) =>
      httpPostJsonResponse(
        `${resource}/noauth/changePassword/`,
        { body: JSON.stringify({ newPassword, oldPassword, email }) },
        true,
        false,
      ),
    [resource, httpPostJsonResponse],
  );

  const resetPassword = useCallback(
    ({ newPassword }: ResetPasswordRequest) =>
      httpPostJsonResponse(`${resource}/resetPassword/`, {
        body: JSON.stringify({ newPassword }),
      }),
    [resource, httpPostJsonResponse],
  );

  const submitPasswordChangeRequest = useCallback(
    (formData: any) =>
      httpGetEmptyResponse(
        `${resource}/reset/${formData.email}`,
        {},
        true,
        false,
      ),
    [resource, httpGetEmptyResponse],
  );

  const isPasswordValid = useCallback(
    (passwordValidationRequest: PasswordValidationRequest) =>
      httpPostJsonResponse(
        `${resource}/noauth/isPasswordValid`,
        {
          body: JSON.stringify(passwordValidationRequest),
        },
      ),
    [resource, httpPostJsonResponse],
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
