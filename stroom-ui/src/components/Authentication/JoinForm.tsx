// This is based on code from here:
// https://www.digitalocean.com/community/tutorials/how-to-build-a-password-strength-meter-in-react

import * as React from "react";

import FormField from "./FormField";
import NewPasswordField from "./NewPasswordField";
import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import { FormikProps } from "formik";
import { Button } from "antd";

export interface FormValues {
  fullname: string;
  email: string;
  password: string;
}

export interface Props {
  strength: number;
  minStrength: number;
  thresholdLength: number;
}

export const JoinForm: React.FunctionComponent<
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
            <FormField
              name="fullname"
              type="text"
              label="Full Name"
              placeholder="Enter Full Name"
              onChange={handleChange}
              onBlur={handleBlur}
              value={values.fullname}
              error={errors.fullname}
              touched={touched.fullname}
              setFieldTouched={setFieldTouched}
            />

            {/** Render the email field component **/}
            <FormField
              name="email"
              type="text"
              label="Email"
              placeholder="Enter Email Address"
              onChange={handleChange}
              onBlur={handleBlur}
              value={values.email}
              error={errors.email}
              touched={touched.email}
              setFieldTouched={setFieldTouched}
            />

            {/** Render the password field component using thresholdLength of 7 and minStrength of 3 **/}
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
          </div>
        </div>
      </form>
    </FormContainer>
  </LogoPage>
);

export default JoinForm;
