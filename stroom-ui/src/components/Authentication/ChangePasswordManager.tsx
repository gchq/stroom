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
import { FunctionComponent, useState } from "react";
import { ConfirmCurrentPassword } from "./ConfirmCurrentPassword";
import {
  ChangePasswordDialog,
  ChangePasswordFormValues,
} from "./ChangePassword";
import { FormikHelpers } from "formik/dist/types";
import { ChangePasswordRequest } from "./api/types";
import { useAuthenticationResource } from "./api";
import { usePrompt } from "../Prompt/PromptDisplayBoundary";
import { usePasswordPolicy } from "./usePasswordPolicy";

export interface FormValues {
  userId: string;
  password: string;
}

const ChangePasswordManager: FunctionComponent<{
  userId: string;
  onClose: () => void;
}> = (props) => {
  const [state, setState] = useState({
    userId: undefined,
    currentPassword: undefined,
  });

  const passwordPolicyConfig = usePasswordPolicy();
  const { changePassword } = useAuthenticationResource();
  const { showError } = usePrompt();

  if (!state || !state.currentPassword) {
    const onClose = (userId: string, password: string) => {
      if (userId == null && password == null) {
        props.onClose();
      } else {
        setState({ userId, currentPassword: password });
      }
    };
    return <ConfirmCurrentPassword userId={props.userId} onClose={onClose} />;
  } else {
    const onSubmit = (
      values: ChangePasswordFormValues,
      actions: FormikHelpers<ChangePasswordFormValues>,
    ) => {
      const request: ChangePasswordRequest = {
        userId: values.userId,
        currentPassword: state.currentPassword,
        newPassword: values.password,
        confirmNewPassword: values.confirmPassword,
      };

      changePassword(request).then((response) => {
        if (!response) {
          actions.setSubmitting(false);
        } else if (response.changeSucceeded) {
          props.onClose();
        } else {
          actions.setSubmitting(false);
          showError({
            message: response.message,
          });

          // If the user is asked to sign in again then unset the auth state.
          if (response.forceSignIn) {
            props.onClose();
          }
        }
      });
    };

    const onClose = () => {
      props.onClose();
    };

    return (
      <ChangePasswordDialog
        initialValues={{
          userId: state.userId,
          password: "",
          confirmPassword: "",
        }}
        passwordPolicyConfig={passwordPolicyConfig}
        onSubmit={onSubmit}
        onClose={onClose}
        {...props}
      />
    );
  }
};

export default ChangePasswordManager;
