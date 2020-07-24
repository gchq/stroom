// This is based on code from here:
// https://www.digitalocean.com/community/tutorials/how-to-build-a-password-strength-meter-in-react

import * as React from "react";

import { TextBoxFormField, NewPasswordFormField } from "components/FormField";
import BackgroundLogo from "../Layout/BackgroundLogo";
import FormContainer from "../Layout/FormContainer";
import { FormikProps } from "formik";
import { Button } from "antd";
import { PasswordStrengthProps } from "../FormField/NewPasswordFormField";

export interface FormValues {
  fullname: string;
  email: string;
  password: string;
}

export const JoinForm: React.FunctionComponent<{
  formikProps: FormikProps<FormValues>;
  passwordStrengthProps: PasswordStrengthProps;
}> = ({ formikProps, passwordStrengthProps }) => {
  const { handleSubmit, isSubmitting } = formikProps;
  return (
    <BackgroundLogo>
      <FormContainer>
        <form onSubmit={handleSubmit}>
          <div className="JoinForm__content">
            <div className="d-flex flex-row justify-content-between align-items-center px-3 mb-5">
              <legend className="form-label mb-0">Support Team</legend>
              {/** Show the form button only if all fields are valid **/}
              <Button
                className="btn btn-primary text-uppercase px-3 py-2"
                type="primary"
                loading={isSubmitting}
                htmlType="submit"
              >
                Join
              </Button>
            </div>

            <div className="py-5 border-gray border-top border-bottom">
              {/** Render the fullname form field passing the name validation fn **/}
              <TextBoxFormField
                controlId="fullname"
                type="text"
                label="Full Name"
                placeholder="Enter Full Name"
                autoFocus={true}
                formikProps={formikProps}
              />

              {/** Render the email field component **/}
              <TextBoxFormField
                controlId="email"
                type="text"
                label="Email"
                placeholder="Enter Email Address"
                formikProps={formikProps}
              />

              {/** Render the password field component using thresholdLength of 7 and minStrength of 3 **/}
              <NewPasswordFormField
                controlId="password"
                label="Password"
                placeholder="Enter Password"
                passwordStrengthProps={passwordStrengthProps}
                formikProps={formikProps}
              />
            </div>
          </div>
        </form>
      </FormContainer>
    </BackgroundLogo>
  );
};
