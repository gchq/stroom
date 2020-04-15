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
import {
  FunctionComponent,
  SelectHTMLAttributes,
  useEffect,
  useState,
} from "react";

interface Props {
  options?: SelectOption[];
  selected?: string;
  placeholder?: string;
}

export interface SelectOption {
  value: string;
  label: string;
}

const InlineSelect: FunctionComponent<
  Props & SelectHTMLAttributes<HTMLSelectElement>
> = ({ options, onChange, selected, placeholder, autoFocus, ...rest }) => {
  const [isEditing, setEditing] = useState(false);

  // Honor the autoFocus
  useEffect(() => setEditing(autoFocus), [autoFocus]);

  if (selected === undefined) {
    selected = "__default__";
  }

  if (isEditing) {
    return (
      <select
        className="inline-select__editing"
        onBlur={() => setEditing(false)}
        onChange={onChange}
        value={selected}
        {...rest}
      >
        <option disabled value="__default__">
          --please select--
        </option>
        {options.map(option => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    );
  } else {
    let placeholderText = placeholder || <em>click to choose</em>;
    let textToDisplay: string = undefined;
    if (!!selected && selected !== "__default__") {
      const selectedOption = options.find(option => option.value === selected);
      textToDisplay = !!selectedOption
        ? selectedOption.label
        : "Error: unknown option: " + selected;
    }
    return (
      <span
        className="inline-select__not-editing"
        onClick={() => setEditing(true)}
      >
        {textToDisplay || placeholderText}
      </span>
    );
  }
};

export default InlineSelect;
