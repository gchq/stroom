import * as queryString from "query-string";
import { useCallback, useMemo } from "react";
import { useHttpClient2 } from "lib/useHttpClient";
import { useLocation } from "react-router-dom";
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
  // let redirectUri: string;

  const location = useLocation();
  // if (!!router && !!router.location) {
  //   const query = queryString.parse(router.location.search);
  //   if (!!query.redirect_uri) {
  //     redirectUri = query.redirect_uri + "";
  //   }
  // }

  const redirectUri: string = useMemo(() => {
    if (!!location) {
      const query = queryString.parse(location.search);
      if (!!query.redirect_uri) {
        return query.redirect_uri + "";
      }
    }
  }, [location]);

  const login = useCallback(
    (request: LoginRequest) => {
      const uri = encodeURI(redirectUri);
      const url = `${resource}/noauth/login?redirect_uri=${uri}`;
      return post(url, request);
    },
    [resource, redirectUri, post],
  );

  const confirmPassword = useCallback(
    (request: ConfirmPasswordRequest) => {
      const uri = encodeURI(redirectUri);
      const url = `${resource}/noauth/confirmPassword?redirect_uri=${uri}`;
      return post(url, request);
    },
    [resource, redirectUri, post],
  );

  const changePassword = useCallback(
    (request: ChangePasswordRequest) => {
      const uri = encodeURI(redirectUri);
      const url = `${resource}/noauth/changePassword?redirect_uri=${uri}`;
      return post(url, request);
    },
    [resource, redirectUri, post],
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
