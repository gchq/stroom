import * as React from "react";

import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import { Button } from "antd";
import { FormikProps } from "formik";
import PasswordField from "./PasswordField";
import NewPasswordField from "./NewPasswordField";

export interface FormValues {
  password: string;
  confirmPassword: string;
}

export interface Props {
  strength: number;
  minStrength: number;
  thresholdLength: number;
}

export const ChangePasswordForm: React.FunctionComponent<
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
  strength,
  minStrength,
  thresholdLength,
}) => (
  <LogoPage>
    <FormContainer>
      <form onSubmit={handleSubmit}>
        <div className="JoinForm__content">
          <div className="d-flex flex-row justify-content-between align-items-center mb-3">
            <legend className="form-label mb-0">Change Password</legend>
          </div>

          <NewPasswordField
            name="password"
            label="Password"
            placeholder="Enter Password"
            strength={strength}
            minStrength={minStrength}
            thresholdLength={thresholdLength}
            onChange={handleChange}
            onBlur={handleBlur}
            value={values.password}
            error={errors.password}
            touched={touched.password}
            setFieldTouched={setFieldTouched}
          />

          <PasswordField
            name="confirmPassword"
            label="Confirm Password"
            placeholder="Confirm Password"
            className="no-icon-padding right-icon-padding hide-background-image"
            onChange={handleChange}
            onBlur={handleBlur}
            value={values.confirmPassword}
            error={errors.confirmPassword}
            touched={touched.confirmPassword}
            setFieldTouched={setFieldTouched}
          />

          <div className="SignIn__actions page__buttons Button__container">
            <Button
              className="SignIn__button"
              type="primary"
              loading={isSubmitting}
              htmlType="submit"
            >
              Change Password
            </Button>
          </div>
        </div>
      </form>
    </FormContainer>
  </LogoPage>
);

export default ChangePasswordForm;
