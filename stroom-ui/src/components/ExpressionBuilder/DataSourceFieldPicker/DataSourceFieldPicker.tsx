import InlineSelect, {
  SelectOption,
} from "components/InlineSelect/InlineSelect";
import * as React from "react";
import { DataSourceType } from "../types";

interface Props {
  dataSource: DataSourceType;
  value: string | undefined;
  onChange: (e: string) => void;
}

const DataSourceFieldPicker: React.FunctionComponent<Props> = ({
  dataSource,
  value,
  onChange,
}) => {
  // Convert value list to value/label list
  const options: SelectOption[] = React.useMemo(
    () => dataSource.fields.map(({ name }) => ({ value: name, label: name })),
    [dataSource],
  );

  return (
    <InlineSelect
      options={options}
      selected={value}
      onChange={(event) => {
        onChange(event.target.value);
      }}
    />
  );
};

export default DataSourceFieldPicker;
