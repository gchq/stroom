import * as React from "react";

import SelectBox, { ControlledInputProps } from "../SelectBox";
import permissionInheritanceValues from "./permissionInheritanceValues";

const piOptions = Object.values(permissionInheritanceValues).map(pi => ({
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
