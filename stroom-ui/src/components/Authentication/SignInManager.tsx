/*
 * Copyright 2020 Crown Copyright
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
import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import { useMemo, useState } from "react";
import { AuthState } from "./api/types";
import SignInForm from "./SignInForm";
import { AuthStateProps } from "./ConfirmCurrentPasswordForm";
import ChangePasswordForm from "./ChangePasswordForm";
import * as queryString from "query-string";
import { useLocation } from "react-router-dom";

export interface FormValues {
  userId: string;
  password: string;
}

export interface PageProps {
  allowPasswordResets?: boolean;
}

const Page: React.FunctionComponent = () => {
  const [authState, setAuthState] = useState<AuthState>();
  const props: AuthStateProps = {
    authState,
    setAuthState,
  };

  const location = useLocation();
  const redirectUri: string = useMemo(() => {
    if (!!location) {
      const query = queryString.parse(location.search);
      if (!!query.redirect_uri) {
        return query.redirect_uri + "";
      }
    }
  }, [location]);

  if (!authState) {
    return <SignInForm {...props} />;
  } else if (authState.requirePasswordChange) {
    // if (authState.requireCredentialConfirmation) {
    //   return <ConfirmCurrentPasswordForm {...props} />;
    // }
    return <ChangePasswordForm {...props} />;
  }

  window.location.href = redirectUri;
  return (
    <div className="JoinForm__content">
      <div className="d-flex flex-row justify-content-between align-items-center mb-3">
        <legend className="form-label mb-0">Loading. Please wait...</legend>
      </div>
    </div>
  );
};

export const SignInManager: React.FunctionComponent<PageProps> = ({
  allowPasswordResets,
  children,
}) => (
  <LogoPage>
    <FormContainer>
      <Page />
    </FormContainer>
  </LogoPage>
);

export default SignInManager;
