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
  value?: string;
  error?: string;
  touched?: boolean;
  setFieldTouched?: (name: string) => void;
}

export interface FormFieldType {
  type: "text" | "password";
}

export interface FormFieldProps extends CustomControlProps {
  label: string;
}

export interface CustomControlProps {
  controlId?: string;
  placeholder: string;
  autoComplete?: string;
  className?: string;
  validator?: (label: string, value: string) => void;
  onChange?: ChangeEventHandler<any>;
  onBlur?: FocusEventHandler<any>;
  autoFocus?: boolean;
}

export const CustomControl: FunctionComponent<
  CustomControlProps & FormFieldState & FormFieldType
> = ({
  controlId,
  type,
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
        type={type}
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

export const FormField: FunctionComponent<
  FormFieldProps & FormFieldState & FormFieldType
> = ({
  controlId,
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
          controlId={controlId}
          type={type}
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
  show?: boolean;
}

export const CustomModal: FunctionComponent<CustomModalProps> = (props) => {
  const p = {
    show: true,
    onHide: () => undefined,
    centered: true,
    ...props,
  };
  return (
    <Modal {...p} aria-labelledby="contained-modal-title-vcenter">
      {props.children}
    </Modal>
  );
};
