import * as React from "react";
import Select from "react-select";
import {
  IndexFieldType,
  IndexFieldTypeDisplayValues,
} from "components/DocumentEditors/useDocumentApi/types/indexDoc";

interface Props {
  className?: string;
  value?: IndexFieldType;
  onChange: (c: IndexFieldType) => any;
}

interface IndexFieldTypeOption {
  value: IndexFieldType;
  label: string;
}

const OPTIONS: IndexFieldTypeOption[] = Object.entries(
  IndexFieldTypeDisplayValues,
).map(d => ({
  value: d[0] as IndexFieldType,
  label: d[1],
}));

const IndexFieldTypePicker: React.FunctionComponent<Props> = ({
  className,
  value,
  onChange,
}) => (
  <Select
    className={className}
    placeholder="Index Field Type"
    value={OPTIONS.find(o => o.value === value)}
    onChange={(o: IndexFieldTypeOption) => onChange(o.value)}
    options={OPTIONS}
  />
);

export default IndexFieldTypePicker;
