import * as React from "react";

import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import { Button } from "antd";
import { NavLink } from "react-router-dom";
import { FormikProps } from "formik";
import PasswordField from "./PasswordField";

export interface FormValues {
  password: string;
}

export interface Props {
  allowPasswordResets?: boolean;
}

export const CurrentPasswordForm: React.FunctionComponent<
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
        <div className="JoinForm__content">
          <div className="d-flex flex-row justify-content-between align-items-center mb-3">
            <legend className="form-label mb-0">Enter Current Password</legend>
          </div>

          <PasswordField
            name="password"
            label="Password"
            placeholder="Enter Password"
            className="no-icon-padding right-icon-padding hide-background-image"
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
              Validate
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

export default CurrentPasswordForm;
