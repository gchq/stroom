import * as React from "react";
import { FunctionComponent, useState } from "react";
import { TextBox } from "./TextBoxFormField";
import { ViewPassword } from "./ViewPassword";
import { Col, Form } from "react-bootstrap";
import { FormikProps } from "formik";
import { createFormFieldState } from "./util";
import zxcvbn from "zxcvbn";
import { FormFieldState } from "./FormField";
import { PasswordPolicyConfig } from "api/stroom";

export interface PasswordStrengthProps {
  passwordPolicy: PasswordPolicyConfig;
  strength?: number;
  onStrengthChanged?: (strength: number) => void;
}

interface NewPasswordFormFieldProps {
  controlId: string;
  label: string;
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  passwordStrengthProps: PasswordStrengthProps;
  formikProps: FormikProps<any>;
}

export const NewPasswordFormField: FunctionComponent<NewPasswordFormFieldProps> = ({
  controlId,
  label,
  placeholder,
  autoComplete,
  autoFocus,
  formikProps,
  passwordStrengthProps,
  children,
}) => {
  // const [strength, setStrength] = useState(0);
  const createdState = createFormFieldState(controlId, formikProps);
  const changeHandler = (val: string) => {
    // if (e.target.id === "password") {
    const score = zxcvbn(val).score;
    passwordStrengthProps.onStrengthChanged(score);
    // setStrength(score);
    // createdState.onChange(val);
    // formikProps.validateField(controlId);
    // formikProps.setFieldError(controlId, )
    // currentStrength = score;
    // }
    // formikProps.handleChange(e);
    createdState.onChange(val);
    // formikProps.setFieldValue(controlId, val, true);
    // formikProps.validateField(controlId);
  };

  const formFieldState: FormFieldState<string> = {
    ...createdState,
    onChange: changeHandler,
  };

  const { value, error, touched } = formFieldState;
  const { strength, passwordPolicy } = passwordStrengthProps;

  const {
    minimumPasswordStrength = 3,
    minimumPasswordLength = 7,
    passwordPolicyMessage = "To conform with our Strong Password policy, you are required to use a sufficiently strong password. Password must be more than 7characters.",
  } = passwordPolicy;

  // initialize internal component state
  const [passwordVisible, setPasswordVisible] = useState<boolean>(false);

  const viewPasswordToggle = (visible: boolean) => {
    setPasswordVisible(visible);
  };

  const passwordLength = value.length;
  const passwordStrong = strength >= minimumPasswordStrength;
  const passwordLong = passwordLength > minimumPasswordLength;

  // dynamically set the password length counter class
  const counterClass = [
    "badge badge-pill",
    passwordLong
      ? passwordStrong
        ? "badge-success"
        : "badge-warning"
      : "badge-danger",
  ]
    .join(" ")
    .trim();

  // password strength meter is only visible when password is not empty
  const strengthClass = [
    "strength-meter mt-2",
    passwordLength > 0 ? "visible" : "invisible",
  ]
    .join(" ")
    .trim();

  const controlClass = [
    "form-control",
    "allow-focus",
    "hide-background-image length-indicator-padding",
    touched ? (error ? "is-invalid" : "is-valid") : "",
  ]
    .join(" ")
    .trim();

  return (
    <Form.Group as={Col} controlId={controlId}>
      <Form.Label>{label}</Form.Label>
      {/** Render the children nodes passed to component **/}
      <Form.Text className="my-0 text-muted">{passwordPolicyMessage}</Form.Text>
      {/** Render the password strength meter **/}
      <div className={strengthClass}>
        <div className="strength-meter-fill" data-strength={strength} />
      </div>
      <TextBox
        className={controlClass}
        type={passwordVisible ? "text" : "password"}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        state={formFieldState}
      >
        {children}
        <ViewPassword
          state={passwordVisible}
          onStateChanged={viewPasswordToggle}
        />
        <div className="NewPasswordField__password-count position-absolute mx-3">
          {/** Render the password length counter indicator **/}
          <span className={counterClass}>
            {passwordLength
              ? passwordLong
                ? `${minimumPasswordLength}+`
                : passwordLength
              : ""}
          </span>
        </div>
      </TextBox>
      {/** Render the first error if there are any errors **/}
      <Form.Control.Feedback type="invalid">{error}</Form.Control.Feedback>
    </Form.Group>
  );
};
