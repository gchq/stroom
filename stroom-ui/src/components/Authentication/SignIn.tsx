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
import { NavLink } from "react-router-dom";
import { Formik, FormikProps } from "formik";
import { TextBoxFormField, PasswordFormField } from "components/FormField";
import { Person, Lock } from "react-bootstrap-icons";
import useAuthenticationResource from "./api/useAuthenticationResource";
import { usePrompt } from "../Prompt/PromptDisplayBoundary";
import * as Yup from "yup";
import { AuthStateProps } from "./ConfirmCurrentPassword";
import { Col, Form } from "react-bootstrap";
import Button from "components/Button";
import FormContainer from "../Layout/FormContainer";

export interface FormValues {
  userId: string;
  password: string;
}

export const SignInForm: React.FunctionComponent<FormikProps<FormValues>> = ({
  values,
  errors,
  touched,
  setFieldTouched,
  handleChange,
  handleBlur,
  handleSubmit,
  isSubmitting,
}) => (
  <Form noValidate={true} onSubmit={handleSubmit}>
    <TextBoxFormField
      controlId="userId"
      type="text"
      autoComplete="username"
      label="User Name"
      placeholder="Enter User Name"
      className="no-icon-padding left-icon-padding hide-background-image"
      onChange={handleChange}
      onBlur={handleBlur}
      value={values.userId}
      error={errors.userId}
      touched={touched.userId}
      setFieldTouched={setFieldTouched}
      autoFocus={true}
    >
      <Person className="FormField__icon" />
    </TextBoxFormField>

    <PasswordFormField
      controlId="password"
      label="Password"
      autoComplete="current-password"
      placeholder="Enter Password"
      className="left-icon-padding right-icon-padding hide-background-image"
      onChange={handleChange}
      onBlur={handleBlur}
      value={values.password}
      error={errors.password}
      touched={touched.password}
      setFieldTouched={setFieldTouched}
    >
      <Lock className="FormField__icon" />
    </PasswordFormField>
    <Form.Group as={Col} controlId="signInButton" className="my-0">
      <Button
        className="w-100"
        appearance="contained"
        action="primary"
        // icon="check"
        text="OK"
        loading={isSubmitting}
        // onClick={onOk}
      >
        Sign In
      </Button>
    </Form.Group>
  </Form>
);

const SignInFormikWrapper: React.FunctionComponent<AuthStateProps> = ({
  authState,
  setAuthState,
}) => {
  const { login } = useAuthenticationResource();
  const { showError } = usePrompt();
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
            setAuthState({
              ...authState,
              userId: values.userId,
              currentPassword: values.password,
              showInitialChangePassword: response.requirePasswordChange,
            });
          } else {
            actions.setSubmitting(false);
            showError({
              message: response.message,
            });
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
      {(props) => <SignInForm {...props} />}
    </Formik>
  );
};

export const SignInPage: React.FunctionComponent<AuthStateProps> = ({
  authState: { allowPasswordResets },
  children,
}) => (
  <div className="SignIn">
    <div className="SignIn__icon-container">
      <img src={require("../../images/infinity_logo.svg")} alt="Stroom logo" />
    </div>

    {children}

    {allowPasswordResets ? (
      <div className="col text-center">
        <NavLink
          className="SignIn__reset-password"
          to={"/s/resetPasswordRequest"}
        >
          Forgot password?
        </NavLink>
      </div>
    ) : undefined}
  </div>
);

export const SignIn: React.FunctionComponent<AuthStateProps> = (props) => (
  <FormContainer>
    <SignInPage {...props}>
      <SignInFormikWrapper {...props} />
    </SignInPage>
  </FormContainer>
);
