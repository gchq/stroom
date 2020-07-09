import * as React from "react";
import {
  ChangeEventHandler,
  FocusEventHandler,
  FunctionComponent,
  useState,
} from "react";
import { TextBoxFormField } from "./TextBoxFormField";
import { ViewPassword } from "./ViewPassword";
import { FormFieldProps, FormFieldState } from "./FormField";

export interface PasswordFormFieldProps {
  controlId?: string;
  placeholder: string;
  autoComplete?: string;
  className?: string;
  validator?: (label: string, value: string) => void;
  onChange?: ChangeEventHandler<any>;
  onBlur?: FocusEventHandler<any>;
  autoFocus?: boolean;
}

export const PasswordFormField: FunctionComponent<
  PasswordFormFieldProps & FormFieldProps & FormFieldState
> = ({ children, className, ...restProps }) => {
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
    <TextBoxFormField
      {...restProps}
      className={controlClass}
      type={state ? "text" : "password"}
    >
      {children}
      <ViewPassword state={state} onStateChanged={viewPasswordToggle} />
    </TextBoxFormField>
  );
};
