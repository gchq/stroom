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

import { Formik } from "formik";
import * as Yup from "yup";
import * as React from "react";
import useAuthenticationApi from "./api/useAuthenticationApi";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";
import ChangePasswordForm from "./ChangePasswordForm";
import { ChangePasswordRequest } from "./api/types";
import { ValidationError } from "yup";

export const ChangePasswordFormContainer: React.FunctionComponent = () => {
  const { changePassword } = useAuthenticationApi();
  const { alert } = useAlert();

  const strength = 3;
  const minStrength = 3;
  const thresholdLength = 7;

  //
  // Yup.addMethod(Yup.string, "equalTo", function(ref: Ref, msg: string) {
  //   return this.test({
  //     name: "equalTo",
  //     exclusive: false,
  //     message: msg || "${path} must be the same as ${reference}",
  //     params: {
  //       reference: ref.path,
  //     },
  //     test: function(value: string) {
  //       return value === this.resolve(ref);
  //     },
  //   });
  // });

  // function equalTo(ref: Ref, msg: string) {
  //   return this.test({
  //     name: "equalTo",
  //     exclusive: false,
  //     message: msg || "${path} must be the same as ${reference}",
  //     params: {
  //       reference: ref.path,
  //     },
  //     test: function(value: string) {
  //       return value === this.resolve(ref);
  //     },
  //   });
  // };
  //
  // Yup.addMethod(Yup.string, 'equalTo', equalTo);

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required")
    .min(thresholdLength, "Password is short")
    .test("password-strength", "Password is weak", function (value) {
      return new Promise<boolean | ValidationError>((resolve, reject) => {
        const { path, createError } = this;
        // ... test logic
        // if (zxcvbn(value).score < minStrength) {
        reject(createError({ path, message: "Password is weak" }));
        // }
        resolve(true);
      });
    });

  const confirmPasswordSchema = Yup.string()
    .label("Confirm Password")
    .required("Required")
    .test("password-match", "Passwords must match", function (value) {
      const { resolve } = this;
      const ref = Yup.ref("password");
      return value === resolve(ref);
    });

  const validationSchema = Yup.object().shape({
    password: passwordSchema,
    confirmPassword: confirmPasswordSchema,
  });

  return (
    <Formik
      initialValues={{ password: "", confirmPassword: "" }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        const request: ChangePasswordRequest = {
          userId: "argh",
          oldPassword: "asf",
          newPassword: values.password,
          confirmNewPassword: values.confirmPassword,
        };

        changePassword(request).then((response) => {
          if (response.changeSucceeded) {
            // // Otherwise we'll extract what we expect to be the successful login redirect URL
            // Cookies.set("userId", values.userId);
            // window.location.href = response.redirectUri;
          } else {
            actions.setSubmitting(false);
            const error: Alert = {
              type: AlertType.ERROR,
              title: "Error",
              message: response.failedOn[0],
            };
            alert(error);
            // setLoginResultMessage(response.message);
          }
        });
        // login(values);

        // setTimeout(() => {
        //   alert(JSON.stringify(values, null, 2));
        //   actions.setSubmitting(false);
        // }, 1000);
      }}
    >
      {(props) => (
        <ChangePasswordForm
          strength={strength}
          minStrength={minStrength}
          thresholdLength={thresholdLength}
          {...props}
        />
      )}
    </Formik>
  );
};

export default ChangePasswordFormContainer;
