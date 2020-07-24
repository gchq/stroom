import * as React from "react";
import { Form, ControlledInput } from "./types";

/**
 * The form can be given lists of field names for text and checkbox based HTML input elements.
 * It will then generate onChange/value pairs for those fields which can be destructed from
 * the response to useForm.
 */
interface UseForm<T> {
  initialValues?: T;
  onValidate?: (updates: Partial<T>) => void;
}

const reducer = <T extends {}>(state: T, action: Partial<T>) => {
  return { ...state, ...action };
};

export const useForm = <T extends {}>({
  initialValues,
  onValidate,
}: UseForm<T>): Form<T> => {
  const [value, onUpdate] = React.useReducer(reducer, initialValues || {});

  // Set the current values to the initial values, whenever those change
  React.useEffect(() => {
    if (!!initialValues) {
      onUpdate(initialValues);
    }
  }, [initialValues, onUpdate]);

  // Call out to the validation function when the values change
  React.useEffect(() => {
    if (!!onValidate) {
      onValidate(value);
    }
  }, [value, onValidate]);

  const useTextInput = (s: string) => ({
    type: "text",
    onChange: React.useCallback(
      ({ target: { value } }) => onUpdate({ [s]: value } as T),
      [s],
    ),
    value: `${value[s]}`,
  });

  const useCheckboxInput = (s: string) => ({
    type: "checkbox",
    checked: value[s],
    onChange: React.useCallback(() => {
      onUpdate(({
        [s]: !value[s],
      } as unknown) as Partial<T>);
    }, [s]),
  });

  const useControlledInputProps = <FIELD_TYPE>(
    s: string,
  ): ControlledInput<FIELD_TYPE> => ({
    value: (value[s] as unknown) as FIELD_TYPE,
    onChange: React.useCallback((v) => onUpdate(({ [s]: v } as unknown) as T), [
      s,
    ]),
  });

  return {
    onUpdate,
    value,
    useTextInput,
    useCheckboxInput,
    useControlledInputProps,
  };
};

export default useForm;
