import * as queryString from "query-string";
import { useCallback } from "react";
import { useHttpClient2 } from "lib/useHttpClient";
import useRouter from "lib/useRouter";
import {
  ChangePasswordRequest,
  ChangePasswordResponse,
  ConfirmPasswordRequest,
  ConfirmPasswordResponse,
  LoginRequest,
  LoginResponse,
  PasswordValidationRequest,
  PasswordValidationResponse,
  ResetPasswordRequest,
} from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface Api {
  login: (request: LoginRequest) => Promise<LoginResponse>;
  confirmPassword: (
    request: ConfirmPasswordRequest,
  ) => Promise<ConfirmPasswordResponse>;
  changePassword: (
    request: ChangePasswordRequest,
  ) => Promise<ChangePasswordResponse>;
  resetPassword: (
    request: ResetPasswordRequest,
  ) => Promise<ChangePasswordResponse>;
  validatePassword: (
    request: PasswordValidationRequest,
  ) => Promise<PasswordValidationResponse>;
}

export const useAuthenticationApi = (): Api => {
  const { post } = useHttpClient2();
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

  const login = useCallback(
    (request: LoginRequest) => {
      const uri = encodeURI(redirectUri);
      const url = `${resource}/noauth/login?redirect_uri=${uri}`;
      return post(url, request);
    },
    [resource, redirectUri, post],
  );

  const confirmPassword = useCallback(
    (request: ConfirmPasswordRequest) =>
      post(`${resource}/noauth/confirmPassword/`, request),
    [resource, post],
  );

  const changePassword = useCallback(
    (request: ChangePasswordRequest) =>
      post(`${resource}/noauth/changePassword/`, request),
    [resource, post],
  );

  const resetPassword = useCallback(
    (request: ResetPasswordRequest) =>
      post(`${resource}/resetPassword/`, request),
    [resource, post],
  );

  // const submitPasswordChangeRequest = useCallback(
  //   (formData: any) =>
  //     httpGetEmptyResponse(
  //       `${resource}/reset/${formData.email}`,
  //       {},
  //       true,
  //       false,
  //     ),
  //   [resource, httpGetEmptyResponse],
  // );

  const validatePassword = useCallback(
    (request: PasswordValidationRequest) =>
      post(`${resource}/noauth/validatePassword`, request),
    [resource, post],
  );

  return {
    login,
    confirmPassword,
    resetPassword,
    changePassword,
    validatePassword,
  };
};

export default useAuthenticationApi;
