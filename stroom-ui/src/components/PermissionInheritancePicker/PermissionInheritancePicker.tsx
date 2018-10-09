import * as React from "react";

import SelectBox, { ControlledInputProps } from "../SelectBox";
import { PermissionInheritance } from "../../types";

const piOptions = Object.values(PermissionInheritance).map(pi => ({
  value: pi,
  text: pi
}));

const PermissionInheritancePicker = (props: ControlledInputProps) => (
  <SelectBox
    {...props}
    placeholder="Permission Inheritance"
    options={piOptions}
  />
);

export default PermissionInheritancePicker;
