import { useCallback } from "react";
import { useHttpClient2 } from "lib/useHttpClient";
import {
  ChangePasswordRequest,
  ChangePasswordResponse,
  ConfirmPasswordRequest,
  ConfirmPasswordResponse,
  LoginRequest,
  LoginResponse,
  PasswordPolicyConfig,
  ResetPasswordRequest,
  ServerAuthenticationState,
} from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface AuthenticationResource {
  getAuthenticationState: () => Promise<ServerAuthenticationState>;
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
  fetchPasswordPolicyConfig: () => Promise<PasswordPolicyConfig>;
}

export const useAuthenticationResource = (): AuthenticationResource => {
  const { httpGet, httpPost } = useHttpClient2();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/authentication/v1");

  const getAuthenticationState = useCallback(
    () => httpGet(`${resource}/noauth/getAuthenticationState/`),
    [resource, httpGet],
  );

  const login = useCallback(
    (request: LoginRequest) => {
      return httpPost(`${resource}/noauth/login`, request);
    },
    [resource, httpPost],
  );

  const confirmPassword = useCallback(
    (request: ConfirmPasswordRequest) => {
      return httpPost(`${resource}/noauth/confirmPassword`, request);
    },
    [resource, httpPost],
  );

  const changePassword = useCallback(
    (request: ChangePasswordRequest) => {
      return httpPost(`${resource}/noauth/changePassword`, request);
    },
    [resource, httpPost],
  );

  const resetPassword = useCallback(
    (request: ResetPasswordRequest) =>
      httpPost(`${resource}/resetPassword/`, request),
    [resource, httpPost],
  );

  const fetchPasswordPolicyConfig = useCallback(
    () => httpGet(`${resource}/noauth/fetchPasswordPolicy/`),
    [resource, httpGet],
  );

  return {
    getAuthenticationState,
    login,
    confirmPassword,
    resetPassword,
    changePassword,
    fetchPasswordPolicyConfig,
  };
};
