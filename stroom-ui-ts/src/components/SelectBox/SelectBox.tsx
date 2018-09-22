import * as React from "react";

import { OptionType } from "../../types";

export interface Props {
  options: Array<OptionType>;
  value?: string;
  onChange: (a: string) => any;
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
