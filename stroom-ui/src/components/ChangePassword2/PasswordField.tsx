import * as React from "react";
import { FunctionComponent, useState } from "react";
import FormField, { FormFieldProps, FormFieldState } from "./FormField";
import ViewPassword from "./ViewPassword";

const PasswordField: FunctionComponent<FormFieldProps & FormFieldState> = ({
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
      <FormField type={state ? "text" : "password"} {...restProps}>
        {children}
        <ViewPassword state={state} onStateChanged={viewPasswordToggle} />
      </FormField>
    </div>
  );
};

export default PasswordField;
