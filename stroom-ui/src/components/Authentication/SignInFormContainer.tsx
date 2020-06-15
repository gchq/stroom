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

import SignInForm from "./SignInForm";
import { Formik } from "formik";
import * as Yup from "yup";
import * as React from "react";
import useAuthenticationApi from "./api/useAuthenticationApi";
import * as Cookies from "cookies-js";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";

export const SignInFormContainer: React.FunctionComponent = () => {
  const { login } = useAuthenticationApi();
  const { alert } = useAlert();
  return (
    <Formik
      initialValues={{ userId: "", password: "" }}
      validationSchema={Yup.object().shape({
        userId: Yup.string()
          // .email("Email not valid")
          .required("User name is required"),
        password: Yup.string().required("Password is required"),
      })}
      onSubmit={(values, actions) => {
        login(values).then((response) => {
          if (response.loginSuccessful) {
            // Otherwise we'll extract what we expect to be the successful login redirect URL
            Cookies.set("userId", values.userId);
            window.location.href = response.redirectUri;
          } else {
            actions.setSubmitting(false);
            const error: Alert = {
              type: AlertType.ERROR,
              title: "Error",
              message: response.message,
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
      {(props) => <SignInForm allowPasswordResets={true} {...props} />}
    </Formik>
  );
};

export default SignInFormContainer;
