import * as React from "react";

import Select from "react-select";
import { PermissionInheritance } from "./types";
import { ControlledInput } from "lib/useForm/types";
import useReactSelect from "lib/useReactSelect";

const options = Object.entries(PermissionInheritance).map((k) => ({
  value: k[0],
  label: k[1],
}));

const PermissionInheritancePicker: React.FunctionComponent<ControlledInput<
  PermissionInheritance
>> = ({ value, onChange }) => {
  const { _onChange, _value } = useReactSelect({
    options: [],
    onChange,
    value,
  });

  return (
    <Select
      placeholder="Permission Inheritance"
      options={options}
      value={_value}
      onChange={_onChange}
    />
  );
};
export default PermissionInheritancePicker;
