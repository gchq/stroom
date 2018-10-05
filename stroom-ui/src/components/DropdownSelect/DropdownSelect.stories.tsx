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
import { compose } from "recompose";
import { connect } from "react-redux";

import { storiesOf } from "@storybook/react";
import { Field, reduxForm, FormState } from "redux-form";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import DropdownSelect, {
  DropdownOptionProps,
  PickerProps
} from "./DropdownSelect";

import "../../styles/main.css";
import { GlobalStoreState } from "../../startup/reducers";

interface Props {}

interface ConnectState {
  thisForm: FormState;
}
interface ConnectDispatch {}

interface EnhancedProps extends Props, ConnectState {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ form }) => ({
      thisForm: form.dropdownSelectTest
    }),
    {}
  ),
  reduxForm({
    form: "dropdownSelectTest"
  })
);

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

const TestForm = enhance(({ thisForm }: EnhancedProps) => (
  <div>
    <form>
      <div>
        <label>Colour</label>
        <Field
          name="colour"
          component={({ input: { onChange, value } }) => (
            <ColourPicker
              pickerId="colourPicker"
              onChange={onChange}
              value={value}
            />
          )}
        />
      </div>
      <div>
        <label>Weekday</label>
        <Field
          name="weekday"
          component={({ input: { onChange, value } }) => (
            <WeekdayPicker
              pickerId="wdPicker"
              onChange={onChange}
              value={value}
            />
          )}
        />
      </div>
    </form>
    {thisForm &&
      thisForm.values && (
        <form>
          <div>
            <label>Colour:</label>
            <input
              readOnly
              value={thisForm.values.colour}
              onChange={() => {}}
            />
          </div>
          <div>
            <label>Weekday:</label>
            <input
              readOnly
              value={thisForm.values.weekday}
              onChange={() => {}}
            />
          </div>
        </form>
      )}
  </div>
));

storiesOf("Dropdown Select", module)
  .addDecorator(StroomDecorator)
  .add("simple pickers", () => <TestForm />);
