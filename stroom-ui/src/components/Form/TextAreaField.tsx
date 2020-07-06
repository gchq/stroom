import * as React from "react";
import {
  ChangeEventHandler,
  FocusEventHandler,
  FunctionComponent,
  useEffect,
  useRef,
} from "react";
import { Form } from "react-bootstrap";
import { FormField, FormFieldProps, FormFieldState } from "./FormField";

export interface TextAreaProps {
  controlId?: string;
  placeholder: string;
  autoComplete?: string;
  className?: string;
  validator?: (label: string, value: string) => void;
  onChange?: ChangeEventHandler<any>;
  onBlur?: FocusEventHandler<any>;
  autoFocus?: boolean;
}

export const TextArea: FunctionComponent<TextAreaProps & FormFieldState> = ({
  controlId,
  placeholder,
  autoComplete,
  className = "",
  onChange,
  onBlur,
  autoFocus = false,
  value,
  error,
  touched = false,
  setFieldTouched = () => undefined,
  children,
}) => {
  const hasErrors = touched && error;
  const controlClass = [
    "form-control",
    className,
    touched ? (hasErrors ? "is-invalid" : "is-valid") : "",
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
        value={value}
        onChange={(e) => {
          setFieldTouched(controlId);
          onChange(e);
        }}
        onBlur={onBlur}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        ref={inputEl}
      />
      {children}
    </div>
  );
};

export const TextAreaField: FunctionComponent<
  TextAreaProps & FormFieldProps & FormFieldState
> = ({
  controlId,
  label,
  placeholder,
  autoComplete,
  className = "",
  onChange,
  onBlur,
  autoFocus = false,
  value,
  error,
  touched,
  setFieldTouched,
  children,
}) => {
  const hasErrors = touched && error;
  const controlClass = [
    "form-control",
    className,
    touched ? (hasErrors ? "is-invalid" : "is-valid") : "",
  ]
    .join(" ")
    .trim();

  return (
    <FormField
      controlId={controlId}
      label={label}
      error={error}
      touched={touched}
    >
      <TextArea
        controlId={controlId}
        className={controlClass}
        placeholder={placeholder}
        value={value}
        error={error}
        touched={touched}
        setFieldTouched={setFieldTouched}
        onChange={(e) => {
          setFieldTouched(controlId);
          onChange(e);
        }}
        onBlur={onBlur}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
      >
        {children}
      </TextArea>
    </FormField>
  );
};
