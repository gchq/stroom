import * as React from "react";
import { FunctionComponent, useState } from "react";
import FormField, { FormFieldState } from "./FormField";
import ViewPassword from "./ViewPassword";

export interface PasswordFieldProps {
  label: string;
  fieldId: string;
  placeholder: string;
  required?: boolean;
  leftIcon?: any;
  className?: string;
  children?: any;
  validator?: (label: string, value: string) => void;
  onStateChanged?: (state: FormFieldState) => void;
}

const PasswordField: FunctionComponent<PasswordFieldProps> = ({
  validator,
  onStateChanged,
  children,
  ...restProps
}) => {
  // initialize internal component state
  const [state, setState] = useState<boolean>(false);

  const viewPasswordToggle = (viewText: boolean) => {
    setState(viewText);
  };

  return (
    <div className="position-relative">
      {/** Pass the validation and stateChanged functions as props to the form field **/}
      <FormField
        type={state ? "text" : "password"}
        validator={validator}
        onStateChanged={onStateChanged}
        {...restProps}
      >
        {children}
        <ViewPassword state={state} onStateChanged={viewPasswordToggle} />
      </FormField>
    </div>
  );
};

export default PasswordField;
