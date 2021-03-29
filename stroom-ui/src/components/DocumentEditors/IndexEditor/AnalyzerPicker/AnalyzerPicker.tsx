import * as React from "react";
import Select from "react-select";
import {
  AnalyzerDisplayValues,
  AnalyzerType,
} from "components/DocumentEditors/useDocumentApi/types/indexDoc";

interface Props {
  className?: string;
  value?: AnalyzerType;
  onChange: (c: AnalyzerType) => any;
}

interface AnalyzerOption {
  value: string;
  label: string;
}

const OPTIONS: AnalyzerOption[] = Object.entries(AnalyzerDisplayValues).map(
  (d) => ({
    value: d[0],
    label: d[1],
  }),
);

const AnalyzerPicker: React.FunctionComponent<Props> = ({
  className,
  value,
  onChange,
}) => (
  <Select
    className={className}
    placeholder="Index Field Type"
    value={OPTIONS.find((o) => o.value === value)}
    onChange={(o: AnalyzerOption) => onChange(o.value as AnalyzerType)}
    options={OPTIONS}
  />
);

export default AnalyzerPicker;
