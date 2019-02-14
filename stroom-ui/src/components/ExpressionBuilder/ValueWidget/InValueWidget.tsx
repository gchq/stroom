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
import { useState } from "react";

import Button from "../../Button";
import { ControlledInput } from "../../../types";

export interface Props extends ControlledInput<any> {}

const InValueWidget = ({ value, onChange }: Props) => {
  const [inputHasFocus, setInputHasFocus] = useState(false);
  const [composingValue, setComposingValue] = useState("");

  const hasValues = !!value && value.length > 0;
  const splitValues: Array<string> = hasValues ? value.split(",") : [];

  const valueToShow = inputHasFocus ? composingValue : value;

  const onInputFocus = () => {
    setInputHasFocus(true);
  };
  const onInputBlur = () => {
    setInputHasFocus(false);
  };
  const onInputChange: React.ChangeEventHandler<HTMLInputElement> = ({
    target: { value }
  }: React.ChangeEvent<HTMLInputElement>) => {
    setComposingValue(value);
  };
  const onInputSubmit = () => {
    const newValue = splitValues
      .filter(s => s !== composingValue)
      .concat([composingValue])
      .join();
    onChange(newValue);

    setComposingValue("");
  };

  const onInputKeyDown: React.KeyboardEventHandler<HTMLInputElement> = e => {
    if (e.key === "Enter") {
      onInputSubmit();
    }
  };

  const onTermDelete = (term: string) => {
    const newValue = splitValues.filter(s => s !== term).join();
    onChange(newValue);
  };
  return (
    <div className="dropdown">
      <input
        placeholder="Type and hit 'Enter'"
        value={valueToShow}
        onFocus={onInputFocus}
        onBlur={onInputBlur}
        onChange={onInputChange}
        onKeyDown={onInputKeyDown}
      />
      <div className="dropdown__content">
        {splitValues.map(k => (
          <div key={k}>
            {k}
            <Button onClick={e => onTermDelete(k)} text="X" />
          </div>
        ))}
      </div>
    </div>
  );
};

export default InValueWidget;
