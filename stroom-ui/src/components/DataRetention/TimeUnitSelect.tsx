import InlineSelect, {
  SelectOption,
} from "components/InlineSelect/InlineSelect";
import * as React from "react";
import { FunctionComponent, SelectHTMLAttributes } from "react";

const timeUnitOptions: SelectOption[] = [
  { value: "Minutes", label: "minute(s)" },
  { value: "Hours", label: "hour(s)" },
  { value: "Days", label: "day(s)" },
  { value: "Weeks", label: "week(s)" },
  { value: "Months", label: "month(s)" },
  { value: "Years", label: "year(s)" },
];

interface Props {
  selected: string;
}
const TimeUnitSelect: FunctionComponent<
  Props & SelectHTMLAttributes<HTMLSelectElement>
> = ({ onChange, selected }) => {
  return (
    <InlineSelect
      options={timeUnitOptions}
      selected={selected}
      onChange={onChange}
    />
  );
};

export default TimeUnitSelect;
