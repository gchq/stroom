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

// export interface FormFieldState {
//   value: string;
//   dirty: boolean;
//   errors: string[];
// }

const FormField: FunctionComponent<FormFieldProps &
  FormFieldState &
  FormFieldType> = ({
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
  // // initialize state
  // const [state, setState] = useState<FormFieldState>({
  //   value: "",
  //   dirty: false,
  //   errors: [],
  // });
  //
  // // Destructure state.
  // const { value, dirty, errors } = state;
  //
  // const validate = (value: string) => {
  //   // const isEmpty = value.length === 0;
  //   // const requiredMissing = state.dirty && required && isEmpty;
  //
  //   let errors: string[] = [];
  //
  //   // if (requiredMissing) {
  //   //   // if required and is empty, add required error to state
  //   //   errors = [...errors, `${label} is required`];
  //   // } else
  //
  //   if ("function" === typeof validator) {
  //     try {
  //       validator(label, value);
  //     } catch (e) {
  //       // if validator throws error, add validation error to state
  //       errors = [...errors, e.message];
  //     }
  //   }
  //
  //   // update state and call the onStateChanged callback fn after the update
  //   // dirty is only changed to true and remains true on and after the first state update
  //   const newState: FormFieldState = {
  //     value,
  //     errors,
  //     dirty: !state.dirty || state.dirty,
  //   };
  //   setState(newState);
  //   onStateChanged(newState);
  // };

  // const hasChanged = (e: ChangeEvent<HTMLInputElement>) => {
  //   e.preventDefault();
  //
  //   const value = e.target.value;
  //   validate(value);
  // };
  //
  // if (validateOnLoad && !dirty) {
  //   validate(state.value);
  // }

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
        onChange={e => {
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
