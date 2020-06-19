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
import { useState } from "react";
import { AuthState } from "./api/types";
import ConfirmCurrentPasswordForm, {
  AuthStateProps,
} from "./ConfirmCurrentPasswordForm";
import ChangePasswordForm from "./ChangePasswordForm";

export interface FormValues {
  userId: string;
  password: string;
}

const ChangePasswordManager: React.FunctionComponent = () => {
  const [authState, setAuthState] = useState<AuthState>({
    userId: undefined,
    currentPassword: undefined,
    allowPasswordResets: true,
    requirePasswordChange: false,
  });
  const props: AuthStateProps = {
    authState,
    setAuthState,
  };

  if (!authState || !authState.currentPassword) {
    return <ConfirmCurrentPasswordForm {...props} />;
  } else if (authState.requirePasswordChange) {
    return <ChangePasswordForm {...props} />;
  }

  return (
    <div className="JoinForm__content">
      <div className="d-flex flex-row justify-content-between align-items-center mb-3">
        <legend className="form-label mb-0">
          The password has been changed
        </legend>
      </div>
    </div>
  );
};

export default ChangePasswordManager;
