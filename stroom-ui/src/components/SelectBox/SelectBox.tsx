import * as React from "react";

import { OptionType } from "../../types";

export interface ControlledInputProps {
  value?: string;
  onChange: (a: string) => any;
}

export interface Props extends ControlledInputProps {
  options: Array<OptionType>;
  placeholder?: string;
}

const SelectBox = ({
  options,
  value,
  onChange,
  placeholder = "Select an option",
  ...rest
}: Props) => (
  <span className="styled-select">
    <select
      {...rest}
      value={value}
      onChange={({ target: { value } }) => onChange(value)}
    >
      <option value="" disabled>
        {placeholder}
      </option>
      {options.map(f => (
        <option key={f.value} value={f.value}>
          {f.text}
        </option>
      ))}
    </select>
  </span>
);

export default SelectBox;
