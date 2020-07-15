import { FunctionComponent } from "react";
import { Col, Form } from "react-bootstrap";
import * as React from "react";

export interface FormFieldState<T> {
  onChange?: (value: T) => void;
  onBlur?: (e: any) => void;
  value?: T;
  error?: any;
  touched?: any;
}

interface FormFieldProps {
  controlId: string;
  label: string;
  error: string;
}

export const FormField: FunctionComponent<FormFieldProps> = ({
  controlId,
  label,
  error = "",
  children,
}) => {
  return (
    <Form.Group as={Col} controlId={controlId}>
      <Form.Label>{label}</Form.Label>
      <div className="FormField__input-container">{children}</div>
      <Form.Control.Feedback type="invalid">{error}</Form.Control.Feedback>
    </Form.Group>
  );
};
