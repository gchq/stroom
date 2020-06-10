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

import useAuthentication from "components/Oldauthentication/useAuthentication";
import { Formik } from "formik";
import * as Yup from "yup";
import * as React from "react";
import JoinForm from "./JoinForm";
import * as zxcvbn from "zxcvbn";

export const JoinFormContainer: React.FunctionComponent = () => {
  const { login, isSubmitting } = useAuthentication();
  const minStrength = 3;
  const thresholdLength = 7;

  return (
    <Formik
      initialValues={{ fullname: "", email: "", password: "" }}
      validationSchema={Yup.object().shape({
        fullname: Yup.string()
          .required("Full name is required")
          .test("fullname-pattern", "Full name is invalid", value => {
            const regex = /^[a-z]{2,}(\s[a-z]{2,})+$/i;
            return regex.test(value);
          }),
        email: Yup.string()
          .email("Email not valid")
          .required("Email is required"),
        password: Yup.string()
          .required("Password is required")
          .min(thresholdLength, "Password is short")
          .test(
            "password-strength",
            "Password is weak",
            value => zxcvbn(value).score >= minStrength,
          ),
      })}
      onSubmit={(values, actions) => {
        login(values);

        // setTimeout(() => {
        //   alert(JSON.stringify(values, null, 2));
        //   actions.setSubmitting(false);
        // }, 1000);
      }}
    >
      {props => {
        return (
          <JoinForm
            minStrength={minStrength}
            thresholdLength={thresholdLength}
            {...props}
          />
        );
      }}
    </Formik>
  );
};

export default JoinFormContainer;
