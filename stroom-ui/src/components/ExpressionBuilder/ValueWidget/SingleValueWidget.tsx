import * as React from "react";
import { ControlledInput } from "../../../types";

export interface Props extends ControlledInput<any> {
  valueType: string;
}

export const SingleValueWidget = ({ value, onChange, valueType }: Props) => (
  <input
    placeholder="value"
    type={valueType}
    value={value || ""}
    onChange={({ target: { value } }) => onChange(value)}
  />
);

export default SingleValueWidget;
