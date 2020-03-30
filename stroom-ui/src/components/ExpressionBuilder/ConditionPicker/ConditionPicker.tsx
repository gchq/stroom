import InlineSelect, { SelectOption } from "components/InlineSelect/InlineSelect";
import * as React from "react";
import { ConditionDisplayValues, ConditionType, conditionTypes } from "../types";

interface Props {
  conditionOptions?: ConditionType[];
  value?: ConditionType;
  onChange: (c: ConditionType) => any;
}

const ConditionPicker: React.FunctionComponent<Props> = ({
  conditionOptions = conditionTypes,
  value,
  onChange,
}) => {
  const options: SelectOption[] = React.useMemo(
    () =>
      conditionOptions.map(c => ({
        value: c,
        label: ConditionDisplayValues[c],
      })),
    [conditionOptions],
  );

  const selected = options.find(option => option.value === value);
  return (
    <InlineSelect
      selected={!!selected ? selected.value : undefined}
      onChange={(event: React.ChangeEvent<HTMLSelectElement>) => onChange(event.target.value as ConditionType)}
      options={options}
    />
  );
};

export default ConditionPicker;
