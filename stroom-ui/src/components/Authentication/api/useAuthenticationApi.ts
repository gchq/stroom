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
  fetchPasswordPolicyConfig: () => Promise<PasswordPolicyConfig>;
}

export const useAuthenticationApi = (): Api => {
  const { get, post } = useHttpClient2();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/authentication/v1");

  const login = useCallback(
    (request: LoginRequest) => {
      return post(`${resource}/noauth/login`, request);
    },
    [resource, post],
  );

  const confirmPassword = useCallback(
    (request: ConfirmPasswordRequest) => {
      return post(`${resource}/noauth/confirmPassword`, request);
    },
    [resource, post],
  );

  const changePassword = useCallback(
    (request: ChangePasswordRequest) => {
      return post(`${resource}/noauth/changePassword`, request);
    },
    [resource, post],
  );

  const resetPassword = useCallback(
    (request: ResetPasswordRequest) =>
      post(`${resource}/resetPassword/`, request),
    [resource, post],
  );

  const fetchPasswordPolicyConfig = useCallback(
    () => get(`${resource}/noauth/fetchPasswordPolicy/`),
    [resource, get],
  );

  return {
    login,
    confirmPassword,
    resetPassword,
    changePassword,
    fetchPasswordPolicyConfig,
  };
};

export default useAuthenticationApi;
