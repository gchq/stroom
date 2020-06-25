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
import BackgroundLogo from "../Layout/BackgroundLogo";
import { useEffect, useState } from "react";
import { AuthState } from "./api/types";
import { SignIn } from "./SignIn";
import { AuthStateProps } from "./ConfirmCurrentPassword";
import ChangePassword from "./ChangePassword";
import StroomWrapper from "./StroomWrapper";
import Background from "../Layout/Background";
import useAuthenticationApi from "./api/useAuthenticationApi";
import FormContainer from "../Layout/FormContainer";

export interface FormValues {
  userId: string;
  password: string;
}

export interface PageProps {
  allowPasswordResets?: boolean;
}

const Page: React.FunctionComponent = () => {
  // Client state
  const [authState, setAuthState] = useState<AuthState>();

  const { getAuthenticationState } = useAuthenticationApi();
  useEffect(() => {
    if (!authState) {
      getAuthenticationState().then((response) => {
        setAuthState({
          ...authState,
          userId: response.userId,
          allowPasswordResets: response.allowPasswordResets,
        });
      });
    }
  }, [getAuthenticationState, authState, setAuthState]);

  const props: AuthStateProps = {
    authState,
    setAuthState,
  };

  if (!authState) {
    return (
      <BackgroundLogo>
        <FormContainer>Loading. Please wait...</FormContainer>
      </BackgroundLogo>
    );
  } else if (!authState.userId) {
    return (
      <BackgroundLogo>
        <SignIn {...props} />
      </BackgroundLogo>
    );
  } else if (authState.showInitialChangePassword) {
    // if (authState.requireCredentialConfirmation) {
    //   return <ConfirmCurrentPasswordForm {...props} />;
    // }
    return (
      <BackgroundLogo>
        <ChangePassword
          userId={authState.userId}
          currentPassword={authState.currentPassword}
          onClose={(success: boolean) => {
            if (success) {
              setAuthState({
                ...authState,
                showInitialChangePassword: false,
              });
            } else {
              setAuthState(undefined);
            }
          }}
          {...props}
        />
      </BackgroundLogo>
    );
  }

  return <StroomWrapper userId={authState.userId} />;

  // window.location.href = redirectUri;
  // return (
  //   <div className="JoinForm__content">
  //     <div className="d-flex flex-row justify-content-between align-items-center mb-3">
  //       <legend className="form-label mb-0">Loading. Please wait...</legend>
  //     </div>
  //   </div>
  // );
};

export const SignInManager: React.FunctionComponent<PageProps> = () => (
  <Background>
    <Page />
  </Background>
);

export default SignInManager;
