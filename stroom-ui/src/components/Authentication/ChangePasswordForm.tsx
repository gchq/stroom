import * as React from "react";

import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import { Button } from "antd";
import { Formik, FormikProps } from "formik";
import PasswordField from "./PasswordField";
import NewPasswordField from "./NewPasswordField";
import useAuthenticationApi from "./api/useAuthenticationApi";
import { useEffect, useState } from "react";
import { ChangePasswordRequest, PasswordPolicyConfig } from "./api/types";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import { useHistory } from "react-router-dom";
import * as Yup from "yup";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";
import zxcvbn from "zxcvbn";
import Cookies from "cookies-js";

export interface FormValues {
  userId: string;
  password: string;
  confirmPassword: string;
}

export interface Props {
  strength: number;
  minStrength: number;
  thresholdLength: number;
}

export const Form: React.FunctionComponent<Props & FormikProps<FormValues>> = ({
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
  <form onSubmit={handleSubmit}>
    <input
      type="text"
      id="userId"
      value={values.userId}
      onChange={handleChange}
      onBlur={handleBlur}
      autoComplete="username"
      hidden={true}
    />

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
      autoComplete="new-password"
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
      autoComplete="confirm-password"
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
  </form>
);

const FormikWrapper: React.FunctionComponent = () => {
  console.log("Render: ChangePasswordFormContainer");

  const { changePassword, fetchPasswordPolicyConfig } = useAuthenticationApi();

  // Get token config
  const [passwordPolicyConfig, setPasswordPolicyConfig] = useState<
    PasswordPolicyConfig
  >({
    allowPasswordResets: true,
    minimumPasswordStrength: 3,
    minimumPasswordLength: 7,
  });
  useEffect(() => {
    fetchPasswordPolicyConfig().then(
      (passwordPolicyConfig: PasswordPolicyConfig) => {
        setPasswordPolicyConfig(passwordPolicyConfig);
      },
    );
  }, [fetchPasswordPolicyConfig]);
  const {
    passwordComplexityRegex,
    minimumPasswordStrength,
    minimumPasswordLength,
  } = passwordPolicyConfig;

  const { alert } = useAlert();

  const [strength, setStrength] = useState(0);
  let currentStrength = strength;

  const minStrength = minimumPasswordStrength;
  const thresholdLength = minimumPasswordLength;

  const history = useHistory();

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required")
    .min(thresholdLength, "Password is short")
    .matches(new RegExp(passwordComplexityRegex), "Password is invalid")
    .test(
      "password-strength",
      "Password is weak",
      () => currentStrength > minStrength,
    );

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
      initialValues={{
        userId: Cookies.get("userId"),
        password: "",
        confirmPassword: "",
      }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        const request: ChangePasswordRequest = {
          userId: values.userId,
          oldPassword: "asf",
          newPassword: values.password,
          confirmNewPassword: values.confirmPassword,
        };

        changePassword(request).then((response) => {
          if (!response) {
            actions.setSubmitting(false);
          } else if (response.changeSucceeded) {
            history.push(response.redirectUri);
          } else {
            actions.setSubmitting(false);
            const error: Alert = {
              type: AlertType.ERROR,
              title: "Error",
              message: response.failedOn[0],
            };
            alert(error);
          }
        });
      }}
    >
      {(props) => {
        const handler = (e: React.ChangeEvent<HTMLInputElement>) => {
          if (e.target.id === "password") {
            const score = zxcvbn(e.target.value).score;
            setStrength(score);
            currentStrength = score;
          }
          props.handleChange(e);
        };

        return (
          <Form
            {...props}
            strength={strength}
            minStrength={minStrength}
            thresholdLength={thresholdLength}
            handleChange={handler}
          />
        );
      }}
    </Formik>
  );
};

export const Page: React.FunctionComponent = ({ children }) => (
  <LogoPage>
    <FormContainer>
      <div className="JoinForm__content">
        <div className="d-flex flex-row justify-content-between align-items-center mb-3">
          <legend className="form-label mb-0">Change Password</legend>
        </div>

        {children}
      </div>
    </FormContainer>
  </LogoPage>
);

const ChangePasswordForm: React.FunctionComponent = () => (
  <Page>
    <FormikWrapper />
  </Page>
);

export default ChangePasswordForm;
