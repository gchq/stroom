import * as React from "react";
import { FunctionComponent, useState } from "react";
import { TextBox } from "./TextBoxFormField";
import { ViewPassword } from "./ViewPassword";
import { Col, Form } from "react-bootstrap";
import { FormFieldProps, FormFieldState } from "./FormField";
import { PasswordFormFieldProps } from "./PasswordFormField";

export interface NewPasswordFormFieldProps extends PasswordFormFieldProps {
  strength?: number;
  minStrength?: number;
  thresholdLength?: number;
}

export const NewPasswordFormField: FunctionComponent<
  NewPasswordFormFieldProps & FormFieldProps & FormFieldState
> = ({
  controlId,
  label,
  placeholder,
  autoComplete,
  onChange,
  onBlur,
  autoFocus = false,
  value,
  error,
  touched,
  setFieldTouched,
  strength,
  minStrength = 3,
  thresholdLength = 7,
}) => {
  // initialize internal component state
  const [state, setState] = useState<boolean>(false);

  const viewPasswordToggle = (viewText: boolean) => {
    setState(viewText);
  };

  const passwordLength = value.length;
  const passwordStrong = strength >= minStrength;
  const passwordLong = passwordLength > thresholdLength;

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

  const hasErrors = touched && error;
  const controlClass = [
    "form-control",
    "hide-background-image length-indicator-padding",
    touched ? (hasErrors ? "is-invalid" : "is-valid") : "",
  ]
    .join(" ")
    .trim();

  return (
    <Form.Group as={Col} controlId={controlId}>
      <Form.Label>{label}</Form.Label>
      {/** Render the children nodes passed to component **/}
      <Form.Text className="my-0 text-muted">
        {`To conform with our Strong Password policy, you are required to use a
          sufficiently strong password. Password must be more than ${thresholdLength} characters.`}
      </Form.Text>
      {/** Render the password strength meter **/}
      <div className={strengthClass}>
        <div className="strength-meter-fill" data-strength={strength} />
      </div>
      <TextBox
        controlId={controlId}
        type={state ? "text" : "password"}
        className={controlClass}
        placeholder={placeholder}
        value={value}
        error={error}
        onChange={(e) => {
          setFieldTouched(controlId);
          onChange(e);
        }}
        onBlur={onBlur}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        touched={touched}
        setFieldTouched={setFieldTouched}
      >
        <ViewPassword state={state} onStateChanged={viewPasswordToggle} />
        <div className="NewPasswordField__password-count position-absolute mx-3">
          {/** Render the password length counter indicator **/}
          <span className={counterClass}>
            {passwordLength
              ? passwordLong
                ? `${thresholdLength}+`
                : passwordLength
              : ""}
          </span>
        </div>
      </TextBox>
      {/** Render the first error if there are any errors **/}
      <Form.Control.Feedback type="invalid">
        {touched ? error : ""}
      </Form.Control.Feedback>
    </Form.Group>
  );
};
