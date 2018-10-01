import * as React from "react";

import { compose, withHandlers, withProps } from "recompose";
import { ControlledInput } from "../../../types";

export interface Props extends ControlledInput<any> {
  valueType: string;
}

interface WithHandlers {
  onFromValueChange: React.ChangeEventHandler<HTMLInputElement>;
  onToValueChange: React.ChangeEventHandler<HTMLInputElement>;
}

interface WithProps {
  fromValue: string;
  toValue: string;
}

export interface EnhancedProps extends Props, WithHandlers, WithProps {}

const enhance = compose<EnhancedProps, Props>(
  withHandlers({
    onFromValueChange: ({ onChange, value = "" }) => ({
      target: { value }
    }: React.ChangeEvent<HTMLInputElement>) => {
      const parts = value.split(",");
      const existingToValue = parts.length === 2 ? parts[1] : "";
      const newValue = `${value},${existingToValue}`;

      onChange(newValue);
    },

    onToValueChange: ({ onChange, value = "" }) => ({
      target: { value }
    }: React.ChangeEvent<HTMLInputElement>) => {
      const parts = value.split(",");
      const existingFromValue = parts.length === 2 ? parts[0] : "";
      const newValue = `${existingFromValue},${value}`;

      onChange(newValue);
    }
  }),
  withProps(({ value }) => {
    let fromValue = "";
    let toValue = "";
    if (value) {
      const splitValues = value.split(",");
      fromValue = splitValues.length === 2 ? splitValues[0] : "";
      toValue = splitValues.length === 2 ? splitValues[1] : "";
    }

    return {
      fromValue,
      toValue
    };
  })
);

const BetweenValueWidget = ({
  fromValue,
  toValue,
  onFromValueChange,
  onToValueChange,
  valueType
}: EnhancedProps) => (
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

export default enhance(BetweenValueWidget);
