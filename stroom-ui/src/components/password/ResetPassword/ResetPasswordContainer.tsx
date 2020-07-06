/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import { validateAsync } from "components/Account/validation";
import ResetPassword from "./ResetPassword";
import useResetPassword from "./useResetPassword";
import { useTokenValidityCheck } from "./useTokenValidityCheck";
import useUrlFactory from "lib/useUrlFactory";

const ResetPasswordContainer = () => {
  const { resetPassword } = useResetPassword();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/Oldauthentication/v1");

  const {
    isTokenMissing,
    isTokenInvalid,
    isTokenExpired,
  } = useTokenValidityCheck();

  const onValidate = (
    oldPassword: string,
    password: string,
    verifyPassword: string,
    email: string,
  ) => {
    return validateAsync(
      email,
      password,
      verifyPassword,
      resource,
      oldPassword,
    );
  };

  return (
    <ResetPassword
      onSubmit={resetPassword}
      onValidate={onValidate}
      isTokenExpired={isTokenExpired}
      isTokenInvalid={isTokenInvalid}
      isTokenMissing={isTokenMissing}
    />
  );
};

export default ResetPasswordContainer;
