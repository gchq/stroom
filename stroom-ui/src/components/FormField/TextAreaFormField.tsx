import * as React from "react";
import { FunctionComponent, useEffect, useRef } from "react";
import { Form } from "react-bootstrap";
import { FormFieldState, FormField } from "./FormField";
import { FormikProps } from "formik";
import { createFormFieldState } from "./util";

interface TextAreaProps {
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  state: FormFieldState<string>;
}

interface TextAreaFormFieldProps {
  controlId: string;
  label: string;
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  formikProps: FormikProps<any>;
}

export const TextArea: FunctionComponent<TextAreaProps> = ({
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
      <Form.Control
        as="textarea"
        rows={5}
        className={controlClass}
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

export const TextAreaFormField: FunctionComponent<TextAreaFormFieldProps> = ({
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
      <TextArea
        className={className}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        state={formFieldState}
      >
        {children}
      </TextArea>
    </FormField>
  );
};
