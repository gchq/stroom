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
import ChangePasswordFields from "../ChangePasswordFields";
import LogoPage from "../../Layout/LogoPage";
import { ChangePasswordRequest } from "../../authentication/types";
import FormContainer from "../../Layout/FormContainer";

const ChangePasswordForm: React.FunctionComponent<{
  email: string;
  onSubmit: (request: ChangePasswordRequest) => void;
  isSubmitting: boolean;
  showChangeConfirmation?: boolean;
  redirectUri?: string;
  // onValidate: (
  //   oldPassword: string,
  //   newPassword: string,
  //   verifyPassword: string,
  //   email: string,
  // ) => Promise<string>;
}> = ({
  showChangeConfirmation,
  redirectUri,
  email,
  onSubmit,
  isSubmitting,
}) => {
  let title = "Change Password";
  if (showChangeConfirmation && redirectUri) {
    title = "Your password has been changed";
    window.location.href = redirectUri;
  }

  let content;
  if (!showChangeConfirmation) {
    content = (
      <ChangePasswordFields
        email={email}
        redirectUri={redirectUri}
        showOldPasswordField={true}
        onSubmit={onSubmit}
        isSubmitting={isSubmitting}
      />
    );
  } else if (showChangeConfirmation && !redirectUri) {
    content = <p>Your password has been changed.</p>;
  }

  return (
    <LogoPage>
      <FormContainer>
        <h3>{title}</h3>
        {content}
      </FormContainer>
    </LogoPage>
  );
};

export default ChangePasswordForm;
