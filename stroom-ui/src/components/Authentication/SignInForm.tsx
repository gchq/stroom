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
import { NavLink, useHistory } from "react-router-dom";
import { Formik, FormikProps } from "formik";
import { Button } from "antd";
import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import FormField from "./FormField";
import PasswordField from "./PasswordField";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import useAuthenticationApi from "./api/useAuthenticationApi";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import * as Yup from "yup";
import Cookies from "cookies-js";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";

export interface FormValues {
  userId: string;
  password: string;
}

export interface PageProps {
  allowPasswordResets?: boolean;
}

export const Form: React.FunctionComponent<FormikProps<FormValues>> = ({
  values,
  errors,
  touched,
  setFieldTouched,
  handleChange,
  handleBlur,
  handleSubmit,
  isSubmitting,
}) => (
  <form onSubmit={handleSubmit}>
    <FormField
      name="userId"
      type="text"
      autoComplete="username"
      label="User Name"
      placeholder="Enter User Name"
      className="no-icon-padding left-icon-padding hide-background-image"
      leftIcon={<UserOutlined />}
      onChange={handleChange}
      onBlur={handleBlur}
      value={values.userId}
      error={errors.userId}
      touched={touched.userId}
      setFieldTouched={setFieldTouched}
    />

    <PasswordField
      name="password"
      label="Password"
      autoComplete="current-password"
      placeholder="Enter Password"
      className="left-icon-padding right-icon-padding hide-background-image"
      leftIcon={<LockOutlined />}
      onChange={handleChange}
      onBlur={handleBlur}
      value={values.password}
      error={errors.password}
      touched={touched.password}
      setFieldTouched={setFieldTouched}
    />

    <div className="SignIn__actions page__buttons Button__container">
      <Button
        className="SignIn__button"
        type="primary"
        loading={isSubmitting}
        htmlType="submit"
      >
        Sign In
      </Button>
    </div>
  </form>
);

export const FormikWrapper: React.FunctionComponent = () => {
  const { login } = useAuthenticationApi();
  const { alert } = useAlert();
  const history = useHistory();
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
          if (!response) {
            actions.setSubmitting(false);
          } else if (response.loginSuccessful) {
            // Otherwise we'll extract what we expect to be the successful login redirect URL
            Cookies.set("userId", values.userId);
            // if (response.redirectUri.indexOf("/s/") != -1) {
            history.push(response.redirectUri);
            // } else {
            //   window.location.href
            // }
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
      {(props) => <Form {...props} />}
    </Formik>
  );
};

export const Page: React.FunctionComponent<PageProps> = ({
  allowPasswordResets,
  children,
}) => (
  <LogoPage>
    <FormContainer>
      <div className="SignIn__content">
        <div className="SignIn__icon-container">
          <img
            src={require("../../images/infinity_logo.svg")}
            alt="Stroom logo"
          />
        </div>

        {children}

        {allowPasswordResets ? (
          <NavLink
            className="SignIn__reset-password"
            to={"/s/resetPasswordRequest"}
          >
            Forgot password?
          </NavLink>
        ) : undefined}
      </div>
    </FormContainer>
  </LogoPage>
);

const SignInForm: React.FunctionComponent = () => (
  <Page>
    <FormikWrapper />
  </Page>
);

export default SignInForm;
