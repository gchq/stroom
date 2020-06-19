import * as React from "react";

import { Button } from "antd";
import { Formik, FormikProps } from "formik";
import PasswordField from "./PasswordField";
import NewPasswordField from "./NewPasswordField";
import useAuthenticationApi from "./api/useAuthenticationApi";
import { useEffect, useState } from "react";
import { ChangePasswordRequest, PasswordPolicyConfig } from "./api/types";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import * as Yup from "yup";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";
import zxcvbn from "zxcvbn";
import { AuthStateProps } from "./ConfirmCurrentPasswordForm";

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

const FormikWrapper: React.FunctionComponent<AuthStateProps> = ({
  authState,
  setAuthState,
}) => {
  const { changePassword, fetchPasswordPolicyConfig } = useAuthenticationApi();

  // Get token config
  const [passwordPolicyConfig, setPasswordPolicyConfig] = useState<
    PasswordPolicyConfig
  >(undefined);
  useEffect(() => {
    console.log("Fetching password policy config");
    fetchPasswordPolicyConfig().then(
      (passwordPolicyConfig: PasswordPolicyConfig) => {
        setPasswordPolicyConfig(passwordPolicyConfig);
      },
    );
  }, [fetchPasswordPolicyConfig]);
  const { alert } = useAlert();
  const [strength, setStrength] = useState(0);

  if (passwordPolicyConfig === undefined) {
    return <div>Loading...</div>;
  }

  const {
    passwordComplexityRegex,
    minimumPasswordStrength,
    minimumPasswordLength,
  } = passwordPolicyConfig;

  let currentStrength = strength;

  const minStrength = minimumPasswordStrength;
  const thresholdLength = minimumPasswordLength;

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

  console.log("Render: ChangePasswordFormContainer");

  return (
    <Formik
      initialValues={{
        userId: authState.userId,
        password: "",
        confirmPassword: "",
      }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        const request: ChangePasswordRequest = {
          userId: values.userId,
          currentPassword: authState.currentPassword,
          newPassword: values.password,
          confirmNewPassword: values.confirmPassword,
        };

        changePassword(request).then((response) => {
          if (!response) {
            actions.setSubmitting(false);
          } else if (response.changeSucceeded) {
            setAuthState({
              ...authState,
              currentPassword: undefined,
              requirePasswordChange: false,
            });
          } else {
            actions.setSubmitting(false);
            const error: Alert = {
              type: AlertType.ERROR,
              title: "Error",
              message: response.message,
            };
            alert(error);

            // If the user is asked to sign in again then unset the auth state.
            if (response.forceSignIn) {
              setAuthState(undefined);
            }
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
  <div className="JoinForm__content">
    <div className="d-flex flex-row justify-content-between align-items-center mb-3">
      <legend className="form-label mb-0">Change Password</legend>
    </div>

    {children}
  </div>
);

const ChangePasswordForm: React.FunctionComponent<AuthStateProps> = (props) => (
  <Page>
    <FormikWrapper {...props} />
  </Page>
);

export default ChangePasswordForm;
