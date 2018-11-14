import * as React from "react";
import Select from "react-select";
import { SelectOptionType, ConditionType, SelectOptionsType } from "src/types";

interface Props {
  className?: string;
  conditionOptions: SelectOptionsType;
  value?: ConditionType;
  onChange: (c: ConditionType) => any;
}

const ConditionPicker = ({
  className,
  conditionOptions,
  value,
  onChange
}: Props) => (
  <Select
    className={className}
    placeholder="Condition"
    value={conditionOptions.find(o => o.value === value)}
    onChange={(o: SelectOptionType) => onChange(o.value as ConditionType)}
    options={conditionOptions}
  />
);

export default ConditionPicker;
