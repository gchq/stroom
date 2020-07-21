import { storiesOf } from "@storybook/react";
import * as React from "react";
import { DatePickerFormField } from "./DatePickerFormField";
import { Formik } from "formik";

storiesOf("FormField", module).add("DatePicker", () => (
  <Formik
    initialValues={{ datePicker: new Date().getTime() }}
    onSubmit={() => undefined}
  >
    {(formikProps) => {
      return (
        <DatePickerFormField
          controlId="datePicker"
          label="Date Picker"
          placeholder="Choose Date"
          className="hide-background-image"
          formikProps={formikProps}
        />
      );
    }}
  </Formik>
));
