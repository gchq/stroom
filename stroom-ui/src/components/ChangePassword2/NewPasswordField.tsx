import * as React from "react";
import { FunctionComponent, useState } from "react";
import FormField, { FormFieldState } from "./FormField";
import * as zxcvbn from "zxcvbn";
import { ZXCVBNScore } from "zxcvbn";
import ViewPassword from "./ViewPassword";
import { IconProp } from "@fortawesome/fontawesome-svg-core";

export interface PasswordFieldState {
  password: string;
  strength: ZXCVBNScore;
  viewText: boolean;
}

export interface NewPasswordFieldProps {
  label: string;
  fieldId: string;
  placeholder: string;
  required?: boolean;
  leftIcon?: IconProp;
  children?: any;
  onStateChanged?: (state: FormFieldState) => void;
  minStrength?: number;
  thresholdLength?: number;
}

const NewPasswordField: FunctionComponent<NewPasswordFieldProps> = ({
  minStrength = 3,
  thresholdLength = 7,
  // type,
  // validator,
  onStateChanged,
  children,
  ...restProps
}) => {
  // set default minStrength to 3 if not a number or not specified
  // minStrength must be a a number between 0 - 4
  minStrength =
    typeof minStrength === "number" ? Math.max(Math.min(minStrength, 4), 0) : 3;

  // set default thresholdLength to 7 if not a number or not specified
  // thresholdLength must be a minimum value of 7
  thresholdLength =
    typeof thresholdLength === "number" ? Math.max(thresholdLength, 7) : 7;

  // initialize internal component state
  const [state, setState] = useState<PasswordFieldState>({
    password: "",
    strength: 0,
    viewText: false,
  });

  // Destructure state.
  const { password, strength, viewText } = state;

  const stateChanged = (e: FormFieldState) => {
    // update the internal state using the updated state from the form field
    const newState: PasswordFieldState = {
      ...state,
      password: e.value,
      strength: zxcvbn(e.value).score,
    };

    setState(newState);
    onStateChanged(e);
  };

  const viewPasswordToggle = (viewText: boolean) => {
    // update the internal state using the updated state from the form field
    const newState: PasswordFieldState = {
      ...state,
      viewText: viewText,
    };

    setState(newState);
    // onStateChanged(e);
  };

  const validatePasswordStrong = (label: string, value: string) => {
    if (value.length === 0) {
      // if required and is empty, add required error to state
      throw new Error(`${label} is required`);
    }

    // ensure password is long enough
    if (value.length <= thresholdLength) {
      throw new Error("Password is short");
    }

    // ensure password is strong enough using the zxcvbn library
    if (zxcvbn(value).score < minStrength) {
      throw new Error("Password is weak");
    }
  };

  const passwordLength = password.length;
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
        type={viewText ? "text" : "password"}
        className="hide-background-image length-indicator-padding"
        validator={validatePasswordStrong}
        onStateChanged={stateChanged}
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
      <ViewPassword state={viewText} onStateChanged={viewPasswordToggle} />
    </div>
  );
};

export default NewPasswordField;
