import * as React from "react";

import Select from "react-select";
import { PermissionInheritance, SelectOptionType } from "../../types";

export interface Props {
  value?: string;
  onChange: (v: string) => any;
}

const options: Array<SelectOptionType> = Object.values(
  PermissionInheritance
).map(o => ({
  value: o,
  label: o
}));

const PermissionInheritancePicker = ({ value, onChange }: Props) => (
  <Select
    value={options.find(o => o.value === value)}
    onChange={(o: SelectOptionType) => onChange(o.value)}
    placeholder="Permission Inheritance"
    options={options}
  />
);

export default PermissionInheritancePicker;
