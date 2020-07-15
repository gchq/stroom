import * as React from "react";
import { FunctionComponent, useEffect, useRef } from "react";
import { FormFieldState, FormField } from "./FormField";

import DatePicker, { registerLocale } from "react-datepicker";

// The following lines are required to start the datepicker week on a Monday.
import enGb from "date-fns/locale/en-GB";
import { FormikProps } from "formik";
import { createFormFieldState } from "./util";
registerLocale("en-gb", enGb);

export interface DatePickerProps {
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  state: FormFieldState<number>;
}

interface DatePickerFormFieldProps {
  controlId: string;
  label: string;
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  formikProps: FormikProps<any>;
}

export const DatePickerControl: FunctionComponent<DatePickerProps> = ({
  className = "",
  placeholder,
  autoComplete,
  autoFocus = false,
  state,
  children,
}) => {
  const { value, error, touched, onChange, onBlur } = state;
  const controlClass = [
    "form-control",
    className,
    touched ? (error ? "is-invalid" : "is-valid") : "",
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
        className={controlClass}
        placeholderText={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        selected={value ? new Date(value) : new Date()}
        onChange={(date) => {
          onChange(date.getTime());
        }}
        onBlur={onBlur}
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

export const DatePickerFormField: FunctionComponent<DatePickerFormFieldProps> = ({
  controlId,
  label,
  className,
  placeholder,
  autoComplete,
  autoFocus,
  formikProps,
  children,
}) => {
  const formFieldState = createFormFieldState(controlId, formikProps);
  return (
    <FormField controlId={controlId} label={label} error={formFieldState.error}>
      <DatePickerControl
        className={className}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        state={formFieldState}
      >
        {children}
      </DatePickerControl>
    </FormField>
  );
};
