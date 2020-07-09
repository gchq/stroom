import { FunctionComponent } from "react";
import { Col, Form } from "react-bootstrap";
import * as React from "react";

export interface FormFieldState {
  value?: string;
  error?: string;
  touched?: boolean;
  setFieldTouched?: (name: string) => void;
}

export interface FormFieldProps {
  controlId: string;
  label: string;
}

export const FormField: FunctionComponent<FormFieldProps & FormFieldState> = ({
  controlId,
  label,
  error,
  touched,
  children,
}) => {
  return (
    <Form.Group as={Col} controlId={controlId}>
      <Form.Label>{label}</Form.Label>
      <div className="FormField__input-container">{children}</div>
      {/** Render the first error if there are any errors **/}
      <Form.Control.Feedback type="invalid">
        {touched ? error : ""}
      </Form.Control.Feedback>
    </Form.Group>
  );
};
