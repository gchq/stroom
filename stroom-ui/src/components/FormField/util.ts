import { FormikProps } from "formik";
import { FormFieldState } from "./FormField";

export const createFormFieldState = (
  controlId: string,
  formikProps: FormikProps<any>,
): FormFieldState<any> => {
  const {
    values,
    setFieldValue,
    errors,
    touched,
    setFieldTouched,
  } = formikProps;

  return {
    onChange: (val) => {
      setFieldTouched(controlId, true, false);
      setFieldValue(controlId, val, true);
    },
    onBlur: () => setFieldTouched(controlId, true, true),
    value: values[controlId],
    error: touched[controlId] ? errors[controlId] : undefined,
    touched: touched[controlId],
  };
};
