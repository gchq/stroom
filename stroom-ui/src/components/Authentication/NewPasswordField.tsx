import * as React from "react";
import { FunctionComponent, useState } from "react";
import FormField, { FormFieldProps, FormFieldState } from "./FormField";
import * as zxcvbn from "zxcvbn";
import ViewPassword from "./ViewPassword";

export interface NewPasswordFieldProps {
  minStrength?: number;
  thresholdLength?: number;
}

const NewPasswordField: FunctionComponent<NewPasswordFieldProps &
  FormFieldProps &
  FormFieldState> = ({
  minStrength = 3,
  thresholdLength = 7,
  children,
  value,
  ...restProps
}) => {
  // initialize internal component state
  const [state, setState] = useState<boolean>(false);

  const viewPasswordToggle = (viewText: boolean) => {
    setState(viewText);
  };

  const strength = zxcvbn(value).score;
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

  return (
    <div className="position-relative">
      {/** Pass the validation and stateChanged functions as props to the form field **/}
      <FormField
        type={state ? "text" : "password"}
        className="hide-background-image length-indicator-padding"
        value={value}
        {...restProps}
      >
        <span className="d-block form-hint">
          To conform with our Strong Password policy, you are required to use a
          sufficiently strong password. Password must be more than 7 characters.
        </span>
        {children}
        {/** Render the password strength meter **/}
        <div className={strengthClass}>
          <div className="strength-meter-fill" data-strength={strength} />
        </div>
      </FormField>
      <div className="position-absolute password-count mx-3">
        {/** Render the password length counter indicator **/}
        <span className={counterClass}>
          {passwordLength
            ? passwordLong
              ? `${thresholdLength}+`
              : passwordLength
            : ""}
        </span>
      </div>
      <ViewPassword state={state} onStateChanged={viewPasswordToggle} />
    </div>
  );
};

export default NewPasswordField;
