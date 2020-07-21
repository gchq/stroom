import * as React from "react";
import { FunctionComponent, useEffect, useRef, useState } from "react";
import { FormFieldState, FormField } from "./FormField";
import { FormikProps } from "formik";
import { createFormFieldState } from "./util";
import { useAccountResource } from "../Account/api";
import { SearchAccountRequest } from "../Account/api/types";
import { AsyncTypeahead, Hint } from "react-bootstrap-typeahead";
import { Form } from "react-bootstrap";

export interface UserSelectControlProps {
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  state: FormFieldState<string>;
}

interface UserSelectFormFieldProps {
  controlId: string;
  label: string;
  className?: string;
  placeholder: string;
  autoComplete?: string;
  autoFocus?: boolean;
  formikProps: FormikProps<any>;
}

export const UserSelectControl: FunctionComponent<UserSelectControlProps> = ({
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

  // return (
  //   <div className="FormField__input-container">
  //     {/*<UserSelect*/}
  //     {/*  // className={controlClass}*/}
  //     {/*  placeholder={placeholder}*/}
  //     {/*  autoComplete={autoComplete}*/}
  //     {/*  autoFocus={autoFocus}*/}
  //     {/*  value={value}*/}
  //     {/*  onChange={(val) => onChange(val)}*/}
  //     {/*  onBlur={onBlur}*/}
  //     {/*  ref={inputEl}*/}
  //     {/*  fuzzy={false}*/}
  //     {/*/>*/}
  //     {children}
  //   </div>
  // );

  const { search } = useAccountResource();
  const [isLoading, setIsLoading] = useState(false);
  const [options, setOptions] = useState([]);

  const handleSearch = (query) => {
    setIsLoading(true);

    const request: SearchAccountRequest = {
      quickFilter: query,
    };

    search(request).then((result) => {
      const options = result.values.map((i) => ({
        id: i.id,
        userId: i.userId,
      }));

      setOptions(options);
      setIsLoading(false);
    });
  };

  const renderInput = ({ inputRef, referenceElementRef, ...inputProps }) => (
    <div className="FormField__input-container">
      <Hint>
        <Form.Control
          {...inputProps}
          className={controlClass}
          // type="text"
          // placeholder={placeholder}
          // autoComplete={autoComplete}
          // autoFocus={autoFocus}
          // value={value}
          // onChange={(e) => onChange(e.target.value)}
          // onBlur={onBlur}
          ref={(node) => {
            inputRef(node);
            referenceElementRef(node);
          }}
        />
      </Hint>
      {children}
    </div>
  );

  return (
    <div className="FormField__input-container">
      <AsyncTypeahead
        id="userSelect"
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        value={value}
        onChange={(selected) =>
          onChange(
            selected
              ? selected.length > 0
                ? selected[0].userId
                : undefined
              : undefined,
          )
        }
        onBlur={onBlur}
        ref={inputEl}
        isLoading={isLoading}
        labelKey="userId"
        minLength={3}
        onSearch={handleSearch}
        options={options}
        renderInput={renderInput}
        renderMenuItemChildren={(option) => <span>{option.userId}</span>}
      />
      {children}
    </div>
  );
};

export const UserFormField: FunctionComponent<UserSelectFormFieldProps> = ({
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
      <UserSelectControl
        className={className}
        placeholder={placeholder}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        state={formFieldState}
      />
      {children}
    </FormField>
  );
};
