import * as React from "react";
import { FunctionComponent, useEffect, useRef } from "react";
import { FormFieldState, FormField } from "./FormField";
import { FormikProps } from "formik";
import { createFormFieldState } from "./util";
// import { UserSelect } from "../UserSelect";

export interface UserSelectControlProps {
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  state: FormFieldState<string>;
}

interface UserSelectFormFieldProps {
  controlId: string;
  label: string;
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  formikProps: FormikProps<any>;
}

export const UserSelectControl: FunctionComponent<UserSelectControlProps> = ({
  className = "",
  placeholder,
  autoComplete,
  autoFocus = false,
  state,
  children,
}) => {
  const { value, error, touched, onChange, onBlur } = state;
  const controlClass = [
    "form-control",
    className,
    touched ? (error ? "is-invalid" : "is-valid") : "",
  ]
    .join(" ")
    .trim();

  // For some reason autofocus doesn't work inside bootstrap modal forms so we need to use an effect.
  const inputEl = useRef(null);
  useEffect(() => {
    if (autoFocus) {
      inputEl.current.focus();
    }
  }, [autoFocus]);

  return (
    <div className="FormField__input-container">
      {/*<UserSelect*/}
      {/*  // className={controlClass}*/}
      {/*  placeholder={placeholder}*/}
      {/*  autoComplete={autoComplete}*/}
      {/*  autoFocus={autoFocus}*/}
      {/*  value={value}*/}
      {/*  onChange={(val) => onChange(val)}*/}
      {/*  onBlur={onBlur}*/}
      {/*  ref={inputEl}*/}
      {/*  fuzzy={false}*/}
      {/*/>*/}
      {children}
    </div>
  );
};

export const UserFormField: FunctionComponent<UserSelectFormFieldProps> = ({
  controlId,
  label,
  className,
  placeholder,
  autoComplete,
  autoFocus,
  formikProps,
  children,
}) => {
  const formFieldState = createFormFieldState(controlId, formikProps);
  return (
    <FormField controlId={controlId} label={label} error={formFieldState.error}>
      <UserSelectControl
        className={className}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        state={formFieldState}
      >
        {children}
      </UserSelectControl>
    </FormField>
  );
};
