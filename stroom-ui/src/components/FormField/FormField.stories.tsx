import { storiesOf } from "@storybook/react";
import * as React from "react";
import { DatePickerFormField } from "./DatePickerFormField";

storiesOf("FormField", module).add("DatePicker", () => (
  <DatePickerFormField
    controlId="datePicker"
    label="Date Picker"
    placeholder="Choose Date"
    className="hide-background-image"
    onChange={() => undefined}
    onBlur={() => undefined}
    value={0}
    error=""
    touched={false}
    setFieldTouched={() => undefined}
  />
));
