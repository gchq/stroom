import * as React from "react";

import { Formik, FormikProps } from "formik";
import { PasswordField } from "../Form/PasswordField";
import { NewPasswordField } from "../Form/NewPasswordField";
import useAuthenticationResource from "./api/useAuthenticationResource";
import { useEffect, useState } from "react";
import { ChangePasswordRequest, PasswordPolicyConfig } from "./api/types";
import { usePrompt } from "../Prompt/PromptDisplayBoundary";
import * as Yup from "yup";
import zxcvbn from "zxcvbn";
import { Form, Modal } from "react-bootstrap";
import { OkCancelButtons, OkCancelProps } from "../Dialog/OkCancelButtons";
import { Dialog } from "components/Dialog/Dialog";

export interface ChangePasswordFormValues {
  userId: string;
  password: string;
  confirmPassword: string;
}

export interface ChangePasswordFormProps {
  strength: number;
  minStrength: number;
  thresholdLength: number;
}

export const ChangePasswordForm: React.FunctionComponent<
  ChangePasswordFormProps &
    FormikProps<ChangePasswordFormValues> &
    OkCancelProps
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
  onCancel,
  cancelClicked,
}) => (
  <Form noValidate={true} onSubmit={handleSubmit} className="ChangePassword">
    <Modal.Header closeButton={false}>
      <Modal.Title id="contained-modal-title-vcenter">
        Change Password
      </Modal.Title>
    </Modal.Header>
    <Modal.Body>
      <input
        type="text"
        id="userId"
        value={values.userId}
        onChange={handleChange}
        onBlur={handleBlur}
        autoComplete="username"
        hidden={true}
      />
      <Form.Row>
        <NewPasswordField
          controlId="password"
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
          autoFocus={true}
          autoComplete="new-password"
        />
      </Form.Row>
      <Form.Row>
        <PasswordField
          controlId="confirmPassword"
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
      </Form.Row>
    </Modal.Body>
    <Modal.Footer>
      <OkCancelButtons
        onOk={() => undefined}
        onCancel={onCancel}
        okClicked={isSubmitting}
        cancelClicked={cancelClicked}
      />
    </Modal.Footer>
  </Form>
);

const ChangePasswordFormik: React.FunctionComponent<{
  userId: string;
  currentPassword: string;
  onClose: (success: boolean) => void;
}> = (props) => {
  const {
    changePassword,
    fetchPasswordPolicyConfig,
  } = useAuthenticationResource();

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
  const { showError } = usePrompt();
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

  const onCancel = () => {
    props.onClose(false);
  };

  return (
    <Formik
      initialValues={{
        userId: props.userId,
        password: "",
        confirmPassword: "",
      }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        const request: ChangePasswordRequest = {
          userId: values.userId,
          currentPassword: props.currentPassword,
          newPassword: values.password,
          confirmNewPassword: values.confirmPassword,
        };

        changePassword(request).then((response) => {
          if (!response) {
            actions.setSubmitting(false);
          } else if (response.changeSucceeded) {
            props.onClose(true);
          } else {
            actions.setSubmitting(false);
            showError({
              message: response.message,
            });

            // If the user is asked to sign in again then unset the auth state.
            if (response.forceSignIn) {
              props.onClose(false);
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
          <ChangePasswordForm
            {...props}
            strength={strength}
            minStrength={minStrength}
            thresholdLength={thresholdLength}
            handleChange={handler}
            onCancel={onCancel}
          />
        );
      }}
    </Formik>
  );
};

export const ChangePassword: React.FunctionComponent<{
  userId: string;
  currentPassword: string;
  onClose: (success: boolean) => void;
}> = (props) => {
  return (
    <Dialog>
      <ChangePasswordFormik {...props} />
    </Dialog>
  );
};

export default ChangePassword;
