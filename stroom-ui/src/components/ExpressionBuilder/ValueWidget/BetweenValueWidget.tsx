import InlineInput from "components/InlineInput/InlineInput";
import { ControlledInput } from "lib/useForm/types";
import * as React from "react";

interface Props extends ControlledInput<any> {
  valueType: string;
}

const BetweenValueWidget: React.FunctionComponent<Props> = ({
  valueType,
  onChange,
  value,
}) => {
  let fromValue = "";
  let toValue = "";
  if (value) {
    const splitValues = value.split(",");
    fromValue = splitValues.length === 2 ? splitValues[0] : "";
    toValue = splitValues.length === 2 ? splitValues[1] : "";
  }

  const onToValueChange = ({
    target: { value },
  }: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = `${fromValue},${value}`;
    onChange(newValue);
  };

  const onFromValueChange = ({
    target: { value },
  }: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = `${value},${toValue}`;
    onChange(newValue);
  };

  return (
    <span>
      [
      <InlineInput
        placeholder="from"
        type={valueType}
        value={fromValue}
        onChange={onFromValueChange}
      />
      <span> to </span>
      <InlineInput
        placeholder="to"
        type={valueType}
        value={toValue}
        onChange={onToValueChange}
      />
      ]
    </span>
  );
};

export default BetweenValueWidget;
