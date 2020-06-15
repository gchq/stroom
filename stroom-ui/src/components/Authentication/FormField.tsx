import * as React from "react";
import {
  ChangeEventHandler,
  FocusEventHandler,
  FunctionComponent,
} from "react";

export interface FormFieldState {
  value: string;
  error: string;
  touched: boolean;
  setFieldTouched: (name: string) => void;
}

export interface FormFieldType {
  type: "text" | "password";
}

export interface FormFieldProps {
  name: string;
  label: string;
  placeholder: string;
  leftIcon?: any;
  className?: string;
  children?: any;
  validator?: (label: string, value: string) => void;
  onChange?: ChangeEventHandler<HTMLInputElement>;
  onBlur?: FocusEventHandler<HTMLInputElement>;
}

const FormField: FunctionComponent<
  FormFieldProps & FormFieldState & FormFieldType
> = ({
  name,
  type,
  label,
  placeholder,
  leftIcon,
  className = "",
  children,
  onChange,
  onBlur,
  value,
  error,
  touched,
  setFieldTouched,
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
    <div className="form-group pb-2 position-relative">
      <div className="d-flex flex-row justify-content-between align-items-center">
        <label htmlFor={name} className="control-label">
          {label}
        </label>
        {/** Render the first error if there are any errors **/}
        {hasErrors && (
          <div className="error form-hint font-weight-bold text-right m-0 mb-2">
            {error}
          </div>
        )}
      </div>
      {/** Render the children nodes passed to component **/}
      {children}
      <input
        type={type}
        className={controlClass}
        id={name}
        placeholder={placeholder}
        value={value}
        onChange={(e) => {
          setFieldTouched(name);
          onChange(e);
        }}
        onBlur={onBlur}
      />
      {leftIcon && <div className="FormField__icon-container">{leftIcon}</div>}
    </div>
  );
};

export default FormField;
