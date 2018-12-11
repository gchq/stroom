/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import * as React from "react";

import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import DropdownSelect, {
  DropdownOptionProps,
  PickerProps
} from "./DropdownSelect";
import { Formik, Field, FieldProps } from "formik";
import FormikDebug from "../../lib/FormikDebug";

import "../../styles/main.css";

const toSimpleOption = (c: string) => ({
  value: c.toLowerCase(),
  text: c
});

const colourOptions = [
  "Red",
  "Orange",
  "Yellow",
  "Green",
  "Blue",
  "Indigo",
  "Violet"
].map(toSimpleOption);

const ColorOption = ({
  option: { text, value },
  onClick,
  inFocus
}: DropdownOptionProps) => (
  <div className={`hoverable ${inFocus ? "inFocus" : ""}`} onClick={onClick}>
    <span style={{ backgroundColor: value, width: "2rem" }}>&nbsp;</span>
    {text}
  </div>
);

const ColourPicker = ({ onChange, value, pickerId }: PickerProps) => (
  <DropdownSelect
    pickerId="colourPicker"
    onChange={onChange}
    value={value}
    options={colourOptions}
    OptionComponent={ColorOption}
  />
);

const weekdayOptions = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday"
].map(toSimpleOption);

const WeekdayPicker = (props: PickerProps) => (
  <DropdownSelect {...props} options={weekdayOptions} />
);

const TestForm = () => (
  <Formik
    initialValues={{
      color: ""
    }}
    onSubmit={(e: any) => console.log("Submitting", e)}
  >
    {({ setFieldValue }: Formik) => (
      <React.Fragment>
        <form>
          <div>
            <label>Colour</label>
            <Field name="colour">
              {({ field: { value } }: FieldProps) => (
                <ColourPicker
                  pickerId="colourPicker"
                  onChange={e => setFieldValue("colour", e)}
                  value={value}
                />
              )}
            </Field>
          </div>
          <div>
            <label>Weekday</label>
            <Field name="weekday">
              {({ field: { value } }: FieldProps) => (
                <WeekdayPicker
                  pickerId="wdPicker"
                  onChange={e => setFieldValue("weekday", e)}
                  value={value}
                />
              )}
            </Field>
          </div>
        </form>
        <FormikDebug />
      </React.Fragment>
    )}
  </Formik>
);

storiesOf("General Purpose/Dropdown Select", module)
  .addDecorator(StroomDecorator)
  .add("simple pickers", () => <TestForm />);
