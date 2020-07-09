import * as React from "react";
import { FocusEventHandler, FunctionComponent, useEffect, useRef } from "react";
import { FormField, FormFieldProps } from "./FormField";
// import DatePicker from "react-datepicker";

import DatePicker, { registerLocale } from "react-datepicker";

// The following lines are required to start the datepicker week on a Monday.
import enGb from "date-fns/locale/en-GB";
registerLocale("en-gb", enGb);

export interface DatePickerProps {
  controlId?: string;
  placeholder?: string;
  autoComplete?: string;
  className?: string;
  validator?: (label: string, value: string) => void;
  onChange?: (value: number) => void;
  onBlur?: FocusEventHandler<any>;
  autoFocus?: boolean;
}

export interface DatePickerState {
  value?: number;
  error?: string;
  touched?: boolean;
}

export const DatePickerControl: FunctionComponent<
  DatePickerProps & DatePickerState
> = ({
  placeholder,
  autoComplete,
  className = "",
  onChange,
  onBlur,
  autoFocus = false,
  value,
  error,
  touched = false,
  children,
}) => {
  const hasErrors = touched && error;
  const controlClass = [
    "form-control",
    className,
    touched ? (hasErrors ? "is-invalid" : "is-valid") : "",
  ]
    .join(" ")
    .trim();

  // For some reason autofocus doesn't work inside bootstrap modal forms so we need to use an effect.
  const inputEl = useRef(null);
  useEffect(() => {
    if (autoFocus) {
      inputEl.current.focus();
    }
  }, [autoFocus]);

  return (
    <div className="FormField__input-container">
      <DatePicker
        selected={value ? new Date(value) : new Date()}
        onChange={(date) => {
          onChange(date.getTime());
        }}
        className={controlClass}
        placeholderText={placeholder}
        onBlur={onBlur}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        ref={inputEl}
        // showYearDropdown
        // todayButton="Today"

        dateFormat="yyyy-MM-dd"
        locale="en-gb" // Start on a Monday
      />
      {children}
    </div>
  );
};

export const DatePickerFormField: FunctionComponent<
  DatePickerProps & FormFieldProps & DatePickerState
> = ({
  controlId,
  label,
  placeholder,
  autoComplete,
  className = "",
  onChange,
  onBlur,
  autoFocus = false,
  value,
  error,
  touched,
  children,
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
    <FormField
      controlId={controlId}
      label={label}
      error={error}
      touched={touched}
    >
      <DatePickerControl
        controlId={controlId}
        className={controlClass}
        placeholder={placeholder}
        value={value}
        error={error}
        touched={touched}
        onChange={onChange}
        onBlur={onBlur}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
      >
        {children}
      </DatePickerControl>
    </FormField>
  );
};
