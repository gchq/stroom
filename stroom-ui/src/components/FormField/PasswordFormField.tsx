import * as React from "react";
import { FunctionComponent, useState } from "react";
import { TextBoxFormField } from "./TextBoxFormField";
import { ViewPassword } from "./ViewPassword";
import { FormikProps } from "formik";

interface PasswordFieldProps {
  controlId: string;
  label: string;
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  formikProps: FormikProps<any>;
}

export const PasswordFormField: FunctionComponent<PasswordFieldProps> = ({
  children,
  className,
  ...restProps
}) => {
  // initialize internal component state
  const [passwordVisible, setPasswordVisible] = useState<boolean>(false);

  const viewPasswordToggle = (visible: boolean) => {
    setPasswordVisible(visible);
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
      type={passwordVisible ? "text" : "password"}
    >
      {children}
      <ViewPassword
        state={passwordVisible}
        onStateChanged={viewPasswordToggle}
      />
    </TextBoxFormField>
  );
};
