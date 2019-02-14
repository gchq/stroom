import * as React from "react";

import { ControlledInput } from "../../../types";

export interface Props extends ControlledInput<any> {
  valueType: string;
}

const BetweenValueWidget = ({ valueType, onChange, value }: Props) => {
  const onFromValueChange = ({
    target: { value }
  }: React.ChangeEvent<HTMLInputElement>) => {
    const parts = value.split(",");
    const existingToValue = parts.length === 2 ? parts[1] : "";
    const newValue = `${value},${existingToValue}`;

    onChange(newValue);
  };

  const onToValueChange = ({
    target: { value }
  }: React.ChangeEvent<HTMLInputElement>) => {
    const parts = value.split(",");
    const existingFromValue = parts.length === 2 ? parts[0] : "";
    const newValue = `${existingFromValue},${value}`;

    onChange(newValue);
  };

  let fromValue = "";
  let toValue = "";
  if (value) {
    const splitValues = value.split(",");
    fromValue = splitValues.length === 2 ? splitValues[0] : "";
    toValue = splitValues.length === 2 ? splitValues[1] : "";
  }

  return (
    <span>
      <input
        placeholder="from"
        type={valueType}
        value={fromValue}
        onChange={onFromValueChange}
      />
      <span className="input-between__divider">to</span>
      <input
        placeholder="to"
        type={valueType}
        value={toValue}
        onChange={onToValueChange}
      />
    </span>
  );
};

export default BetweenValueWidget;
