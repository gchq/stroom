import * as React from "react";
import { ChangeEvent, FunctionComponent, useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

export interface FormFieldProps {
  type: "text" | "password";
  label: string;
  fieldId: string;
  placeholder: string;
  required?: boolean;
  hideValidateIcon?: boolean;
  leftIcon?: any;
  children?: any;
  validator?: (value: string) => void;
  onStateChanged?: (state: FormFieldState) => void;
}

export interface FormFieldState {
  value: string;
  dirty: boolean;
  errors: string[];
}

const FormField: FunctionComponent<FormFieldProps> = ({
  type,
  label,
  fieldId,
  placeholder,
  required,
  hideValidateIcon,
  leftIcon,
  children,
  validator = (value: string) => value,
  onStateChanged = (state: FormFieldState) => state,
}) => {
  // initialize state
  const [state, setState] = useState<FormFieldState>({
    value: "",
    dirty: false,
    errors: [],
  });

  const hasChanged = (e: ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();

    const value = e.target.value;
    const isEmpty = value.length === 0;
    const requiredMissing = state.dirty && required && isEmpty;

    let errors: string[] = [];

    if (requiredMissing) {
      // if required and is empty, add required error to state
      errors = [...errors, `${label} is required`];
    } else if ("function" === typeof validator) {
      try {
        validator(value);
      } catch (e) {
        // if validator throws error, add validation error to state
        errors = [...errors, e.message];
      }
    }

    // update state and call the onStateChanged callback fn after the update
    // dirty is only changed to true and remains true on and after the first state update
    const newState: FormFieldState = {
      value,
      errors,
      dirty: !state.dirty || state.dirty,
    };
    setState(newState);
    onStateChanged(newState);
  };

  // Destructure state.
  const { value, dirty, errors } = state;
  // const { type, label, fieldId, placeholder, children } = this.props;

  const hasErrors = errors.length > 0;
  const controlClass = [
    "form-control",
    dirty ? (hasErrors ? "is-invalid" : "is-valid") : "",
    hideValidateIcon ? "hide-icon" : "",
    leftIcon ? "left-icon" : "",
  ]
    .join(" ")
    .trim();

  return (
    <div className="form-group pb-2 position-relative">
      <div className="d-flex flex-row justify-content-between align-items-center">
        <label htmlFor={fieldId} className="control-label">
          {label}
        </label>
        {/** Render the first error if there are any errors **/}
        {hasErrors && (
          <div className="error form-hint font-weight-bold text-right m-0 mb-2">
            {errors[0]}
          </div>
        )}
      </div>
      {/** Render the children nodes passed to component **/}
      {children}
      <input
        type={type}
        className={controlClass}
        id={fieldId}
        placeholder={placeholder}
        value={value}
        onChange={hasChanged}
      />
      {leftIcon && <div className="FormField__icon-container">{leftIcon}</div>}
    </div>
  );
};

export default FormField;
