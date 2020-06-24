import * as React from "react";
import {
  ChangeEventHandler,
  FocusEventHandler,
  FunctionComponent,
  useEffect,
  useRef,
} from "react";
import { Col, Form, Modal, ModalProps } from "react-bootstrap";

export interface FormFieldState {
  value: string;
  error: string;
  touched: boolean;
  setFieldTouched: (name: string) => void;
}

export interface FormFieldType {
  type: "text" | "password";
}

export interface FormFieldProps extends CustomFormControlProps {
  label: string;
  className?: string;
  controlId?: string;
}

export interface CustomFormControlProps {
  name: string;
  placeholder: string;
  autoComplete?: string;
  className?: string;
  validator?: (label: string, value: string) => void;
  onChange?: ChangeEventHandler<any>;
  onBlur?: FocusEventHandler<any>;
  autoFocus?: boolean;
}

export const CustomControl: FunctionComponent<
  CustomFormControlProps & FormFieldState & FormFieldType
> = ({
  name,
  type,
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
        type={type}
        className={controlClass}
        id={name}
        placeholder={placeholder}
        value={value}
        onChange={(e) => {
          setFieldTouched(name);
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

export const FormField: FunctionComponent<
  FormFieldProps & FormFieldState & FormFieldType
> = ({
  name,
  type,
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
  controlId,
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
    <Form.Group as={Col} controlId={controlId}>
      <Form.Label>{label}</Form.Label>
      <div className="FormField__input-container">
        <CustomControl
          type={type}
          className={controlClass}
          name={name}
          placeholder={placeholder}
          value={value}
          error={error}
          touched={touched}
          setFieldTouched={setFieldTouched}
          onChange={(e) => {
            setFieldTouched(name);
            onChange(e);
          }}
          onBlur={onBlur}
          autoComplete={autoComplete}
          autoFocus={autoFocus}
        >
          {children}
        </CustomControl>
      </div>
      {/** Render the first error if there are any errors **/}
      <Form.Control.Feedback type="invalid">
        {touched ? error : ""}
      </Form.Control.Feedback>
    </Form.Group>
  );
};

export interface CustomModalProps extends ModalProps {
  show: boolean;
}

export const CustomModal: FunctionComponent<CustomModalProps> = (props) => {
  return <Modal {...props}>{props.children}</Modal>;
};
