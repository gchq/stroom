import { FunctionComponent, useEffect, useRef } from "react";
import { Col, Form } from "react-bootstrap";
import * as React from "react";
import { FormikProps } from "formik";
import { createFormFieldState } from "./util";
import { FormFieldState } from "./FormField";

interface CheckBoxProps {
  label: string;
  className?: string;
  autoFocus?: boolean;
  state: FormFieldState<boolean>;
}

interface CheckBoxFormFieldProps {
  controlId: string;
  label: string;
  className?: string;
  autoFocus?: boolean;
  formikProps: FormikProps<any>;
}

export const CheckBox: FunctionComponent<CheckBoxProps> = ({
  label,
  className = "",
  autoFocus = false,
  state,
  children,
}) => {
  const { value, onChange, onBlur } = state;

  // For some reason autofocus doesn't work inside bootstrap modal forms so we need to use an effect.
  const inputEl = useRef(null);
  useEffect(() => {
    if (autoFocus) {
      inputEl.current.focus();
    }
  }, [autoFocus]);

  return (
    <div className="FormField__input-container">
      <Form.Check
        contentEditable
        label={label}
        type="checkbox"
        className={className}
        autoFocus={autoFocus}
        checked={value}
        onChange={(e) => {
          onChange(e.target.checked);
        }}
        onBlur={onBlur}
        ref={inputEl}
      />
      {children}
    </div>
  );
};

export const CheckBoxFormField: FunctionComponent<CheckBoxFormFieldProps> = ({
  controlId,
  label,
  className,
  autoFocus,
  formikProps,
  children,
}) => {
  const formFieldState = createFormFieldState(controlId, formikProps);
  return (
    <Form.Group as={Col} controlId={controlId}>
      <CheckBox
        label={label}
        className={className}
        autoFocus={autoFocus}
        state={formFieldState}
      >
        {children}
      </CheckBox>
    </Form.Group>
  );
};
