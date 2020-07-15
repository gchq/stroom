import * as React from "react";
import { FunctionComponent, useState } from "react";

import { Formik, FormikProps } from "formik";
import { PasswordFormField, NewPasswordFormField } from "components/FormField";
import { PasswordPolicyConfig } from "./api/types";
import * as Yup from "yup";
import { Form, Modal } from "react-bootstrap";
import { OkCancelButtons, OkCancelProps } from "../Dialog/OkCancelButtons";
import { Dialog } from "components/Dialog/Dialog";
import { FormikHelpers } from "formik/dist/types";
import FormContainer from "../Layout/FormContainer";
import { LockFill } from "react-bootstrap-icons";
import { PasswordStrengthProps } from "../FormField/NewPasswordFormField";

export interface ChangePasswordFormValues {
  userId: string;
  password: string;
  confirmPassword: string;
}

export interface ChangePasswordProps {
  title?: string;
  initialValues: ChangePasswordFormValues;
  passwordPolicyConfig: PasswordPolicyConfig;
  onSubmit: (
    values: ChangePasswordFormValues,
    actions: FormikHelpers<ChangePasswordFormValues>,
  ) => void;
  onClose: (success: boolean) => void;
}

interface ChangePasswordFormProps {
  title: string;
  passwordStrengthProps: PasswordStrengthProps;
  formikProps: FormikProps<ChangePasswordFormValues>;
  okCancelProps: OkCancelProps;
}

export const ChangePasswordForm: FunctionComponent<ChangePasswordFormProps> = ({
  title = "Change Password",
  passwordStrengthProps,
  formikProps,
  okCancelProps,
}) => {
  const { values, handleSubmit, isSubmitting } = formikProps;
  const { onCancel, cancelClicked } = okCancelProps;

  return (
    <Form noValidate={true} onSubmit={handleSubmit} className="ChangePassword">
      <Modal.Header closeButton={false}>
        <Modal.Title id="contained-modal-title-vcenter">
          <LockFill className="mr-3" />
          {title}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <input
          type="text"
          id="userId"
          value={values.userId}
          autoComplete="username"
          hidden={true}
        />
        <Form.Row>
          <NewPasswordFormField
            controlId="password"
            label="Password"
            placeholder="Enter Password"
            passwordStrengthProps={passwordStrengthProps}
            autoFocus={true}
            autoComplete="new-password"
            formikProps={formikProps}
          />
        </Form.Row>
        <Form.Row>
          <PasswordFormField
            controlId="confirmPassword"
            label="Confirm Password"
            placeholder="Confirm Password"
            className="no-icon-padding right-icon-padding hide-background-image"
            autoComplete="confirm-password"
            formikProps={formikProps}
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
};

const ChangePasswordFormik: React.FunctionComponent<ChangePasswordProps> = ({
  title,
  initialValues,
  passwordPolicyConfig,
  onSubmit,
  onClose,
}) => {
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
  const passwordStrengthProps: PasswordStrengthProps = {
    strength: currentStrength,
    minStrength,
    thresholdLength,
    onStrengthChanged: (s) => {
      currentStrength = s;
      setStrength(s);
    },
  };

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required")
    .min(thresholdLength, "Password is short")
    .matches(new RegExp(passwordComplexityRegex), "Password is invalid")
    .test("password-strength", "Password is weak", function (value) {
      return currentStrength >= minStrength;
    });

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
    onClose(false);
  };

  return (
    <Formik<ChangePasswordFormValues>
      initialValues={initialValues}
      validationSchema={validationSchema}
      onSubmit={onSubmit}
    >
      {(formikProps) => {
        // const handler = (e: React.ChangeEvent<HTMLInputElement>) => {
        //   if (e.target.id === "password") {
        //     const score = zxcvbn(e.target.value).score;
        //     setStrength(score);
        //     currentStrength = score;
        //   }
        //   formikProps.handleChange(e);
        // };

        return (
          <ChangePasswordForm
            title={title}
            passwordStrengthProps={passwordStrengthProps}
            // formikProps={{ ...formikProps, handleChange: handler }}
            formikProps={formikProps}
            okCancelProps={{ onCancel: onCancel }}
          />
        );
      }}
    </Formik>
  );
};

export const ChangePasswordDialog: React.FunctionComponent<ChangePasswordProps> = (
  props,
) => {
  return (
    <Dialog>
      <ChangePasswordFormik {...props} />
    </Dialog>
  );
};

export const ChangePasswordPage: React.FunctionComponent<ChangePasswordProps> = (
  props,
) => {
  return (
    <FormContainer>
      <ChangePasswordFormik {...props} />
    </FormContainer>
  );
};
