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
import { FormikProps } from "formik";
import { Button } from "antd";
import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import FormField from "./FormField";
import PasswordField from "./PasswordField";
import { UserOutlined, LockOutlined } from "@ant-design/icons";

export interface FormValues {
  userId: string;
  password: string;
}

export interface Props {
  allowPasswordResets?: boolean;
}

export const SignInForm: React.FunctionComponent<
  Props & FormikProps<FormValues>
> = ({
  values,
  errors,
  touched,
  setFieldTouched,
  handleChange,
  handleBlur,
  handleSubmit,
  isSubmitting,
  allowPasswordResets,
}) => (
  <LogoPage>
    <FormContainer>
      <form onSubmit={handleSubmit}>
        <div className="SignIn__content">
          <div className="SignIn__icon-container">
            <img
              src={require("../../images/infinity_logo.svg")}
              alt="Stroom logo"
            />
          </div>

          <FormField
            name="userId"
            type="text"
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

          {allowPasswordResets ? (
            <NavLink
              className="SignIn__reset-password"
              to={"/s/resetPasswordRequest"}
            >
              Forgot password?
            </NavLink>
          ) : undefined}
        </div>
      </form>
    </FormContainer>
  </LogoPage>
);

export default SignInForm;
