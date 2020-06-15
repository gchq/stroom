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

import { storiesOf } from "@storybook/react";
import * as React from "react";
import { ChangeEventHandler } from "react";
import JsonDebug from "testing/JsonDebug";
import InlineInput from "./InlineInput";

storiesOf("General Purpose", module).add("InlineInput", () => {
  const [stringValue, setStringValue] = React.useState<string>("Yoda");
  const onStringValueChange: ChangeEventHandler<HTMLInputElement> = React.useCallback(
    ({ target: { value } }) => setStringValue(value),
    [setStringValue],
  );

  const [numericValue, setNumericValue] = React.useState<string>("10");
  const onNumericValueChange: ChangeEventHandler<HTMLInputElement> = React.useCallback(
    ({ target: { value } }) => setNumericValue(value),
    [setNumericValue],
  );

  const [dateValue, setDateValue] = React.useState<string>("2019-01-01");
  const onDateValueChange: ChangeEventHandler<HTMLInputElement> = React.useCallback(
    ({ target: { value } }) => setDateValue(value),
    [setDateValue],
  );
  return (
    <div style={{ padding: "5em" }}>
      <h1>InlineInput</h1>
      <p>
        An edit-in-place <code>input</code>, to be used inline with text.
      </p>
      <p>Controls when editing are:</p>
      <ul>
        <li>
          <code>esc</code>: discard the change and close <code>input</code>{" "}
        </li>
        <li>
          <code>enter</code>: keep the change and close <code>input</code>{" "}
        </li>
        <li>
          <code>blur</code> the component: keep the change and close{" "}
          <code>input</code>{" "}
        </li>
      </ul>
      <form>
        <h2>Controlled Input</h2>
        <span>I would like to feed </span>
        <InlineInput value={stringValue} onChange={onStringValueChange} />
        <span> to the sarlacc.</span>
        <h2>A numeric input</h2>
        <span>I would like to feed </span>
        <InlineInput
          type="number"
          value={numericValue}
          onChange={onNumericValueChange}
        />{" "}
        jawas
        <span> to the sarlacc.</span>
        <h2>A date input</h2>
        <span>I would like to feed Jabba to the sarlacc on </span>
        <InlineInput
          type="date"
          value={dateValue}
          onChange={onDateValueChange}
        />
        .
      </form>

      <JsonDebug value={{ stringValue, numericValue, dateValue }} />
    </div>
  );
});
