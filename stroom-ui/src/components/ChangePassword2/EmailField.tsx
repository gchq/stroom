import * as React from "react";
import { FunctionComponent } from "react";
import { validate } from "isemail";

import FormField, { FormFieldState } from "./FormField";

interface EmailFieldProps {
  label: string;
  fieldId: string;
  placeholder: string;
  required?: boolean;
  children?: any;
  onStateChanged?: (state: FormFieldState) => void;
}

const EmailField: FunctionComponent<EmailFieldProps> =
  // prevent passing type and validator props from this component to the rendered form field component
  ({ ...restProps }) => {
    // validateEmail function using the validate() method of the isemail package
    const validateEmail = (label: string, value: string) => {
      if (value.length === 0) {
        throw new Error(`${label} is required`);
      } else if (!validate(value)) {
        throw new Error("Email is invalid");
      }
    };

    // pass the validateEmail to the validator prop
    return <FormField type="text" validator={validateEmail} {...restProps} />;
  };

export default EmailField;
