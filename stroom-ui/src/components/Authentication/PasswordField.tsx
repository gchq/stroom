import * as React from "react";
import { FunctionComponent, useState } from "react";
import { FormField, FormFieldProps, FormFieldState } from "./FormField";
import ViewPassword from "./ViewPassword";

const PasswordField: FunctionComponent<FormFieldProps & FormFieldState> = ({
  children,
  className,
  ...restProps
}) => {
  // initialize internal component state
  const [state, setState] = useState<boolean>(false);

  const viewPasswordToggle = (viewText: boolean) => {
    setState(viewText);
  };

  const controlClass = [
    className,
    "right-icon-padding",
    "hide-background-image",
  ]
    .join(" ")
    .trim();

  return (
    <FormField
      {...restProps}
      className={controlClass}
      type={state ? "text" : "password"}
    >
      {children}
      <ViewPassword state={state} onStateChanged={viewPasswordToggle} />
    </FormField>
  );
};

export default PasswordField;
