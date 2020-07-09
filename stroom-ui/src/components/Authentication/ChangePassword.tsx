import * as React from "react";
import { FunctionComponent, useState } from "react";

import { Formik, FormikProps } from "formik";
import { PasswordFormField, NewPasswordFormField } from "components/FormField";
import { PasswordPolicyConfig } from "./api/types";
import * as Yup from "yup";
import zxcvbn from "zxcvbn";
import { Form, Modal } from "react-bootstrap";
import { OkCancelButtons, OkCancelProps } from "../Dialog/OkCancelButtons";
import { Dialog } from "components/Dialog/Dialog";
import { FormikHelpers } from "formik/dist/types";
import FormContainer from "../Layout/FormContainer";
import { LockFill } from "react-bootstrap-icons";
import {
  DatePickerProps,
  DatePickerState,
} from "../FormField/DatePickerFormField";
import { FormFieldProps, FormFieldState } from "../FormField/FormField";

export interface ChangePasswordFormValues {
  userId: string;
  password: string;
  confirmPassword: string;
}

interface PasswordRequirements {
  strength: number;
  minStrength: number;
  thresholdLength: number;
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
  passwordRequirements: PasswordRequirements;
  formikProps: FormikProps<ChangePasswordFormValues>;
  okCancelProps: OkCancelProps;
}

export interface FormControlProps<T> {
  controlId: string;
  onChange?: (value: T) => void;
  onBlur?: (e: any) => void;
  value?: T;
  error?: any;
  touched?: any;
}

const datePickerProps = (
  controlId: string,
  formikProps: FormikProps<any>,
): FormControlProps<string> => {
  const {
    values,
    setFieldValue,
    errors,
    touched,
    setFieldTouched,
    handleBlur,
  } = formikProps;

  return {
    controlId: controlId,
    onChange: (val) => {
      setFieldTouched(controlId, true, true);
      setFieldValue(controlId, val, false);
    },
    onBlur: handleBlur,
    value: values[controlId],
    error: errors[controlId],
    touched: touched[controlId],
  };
};

export const ChangePasswordForm: FunctionComponent<ChangePasswordFormProps> = ({
  title = "Change Password",
  passwordRequirements,
  formikProps,
  okCancelProps,
}) => {
  const { strength, minStrength, thresholdLength } = passwordRequirements;
  const {
    values,
    setFieldValue,
    errors,
    touched,
    setFieldTouched,
    handleChange,
    handleBlur,
    handleSubmit,
    isSubmitting,
  } = formikProps;
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
          onChange={handleChange}
          onBlur={handleBlur}
          autoComplete="username"
          hidden={true}
        />
        <Form.Row>
          <NewPasswordFormField
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
          <PasswordFormField
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
  const passwordRequirements: PasswordRequirements = {
    strength,
    minStrength,
    thresholdLength,
  };

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
    onClose(false);
  };

  return (
    <Formik<ChangePasswordFormValues>
      initialValues={initialValues}
      validationSchema={validationSchema}
      onSubmit={onSubmit}
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
            title={title}
            passwordRequirements={passwordRequirements}
            formikProps={{ ...props, handleChange: handler }}
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
