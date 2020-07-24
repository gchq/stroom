import * as React from "react";
import { FunctionComponent, useEffect, useRef } from "react";
import { Form } from "react-bootstrap";
import { FormField, FormFieldState } from "./FormField";
import { FormikProps } from "formik";
import { createFormFieldState } from "./util";

interface TextBoxProps {
  className?: string;
  type: "text" | "password";
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  state: FormFieldState<string>;
}

interface TextBoxFormFieldProps {
  controlId: string;
  label: string;
  className?: string;
  type: "text" | "password";
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  formikProps: FormikProps<any>;
}

export const TextBox: FunctionComponent<TextBoxProps> = ({
  className = "",
  type,
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
      <Form.Control
        className={controlClass}
        type={type}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={onBlur}
        ref={inputEl}
      />
      {children}
    </div>
  );
};

export const TextBoxFormField: FunctionComponent<TextBoxFormFieldProps> = ({
  controlId,
  label,
  className,
  type,
  placeholder,
  autoComplete,
  autoFocus,
  formikProps,
  children,
}) => {
  const formFieldState = createFormFieldState(controlId, formikProps);
  return (
    <FormField controlId={controlId} label={label} error={formFieldState.error}>
      <TextBox
        className={className}
        type={type}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        state={formFieldState}
      >
        {children}
      </TextBox>
    </FormField>
  );
};
