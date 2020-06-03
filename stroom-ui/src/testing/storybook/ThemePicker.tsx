import * as React from "react";
import Select from "react-select";
import { ThemeOption, themeOptions } from "../../lib/useTheme";

interface Props {
  className?: string;
  value?: string;
  onChange: (c: string) => any;
}

const ThemePicker: React.FunctionComponent<Props> = ({
                                                       className,
                                                       value,
                                                       onChange,
                                                     }) => (
  <Select
    className={className}
    placeholder="Theme Type"
    value={themeOptions.find(o => o.value === value)}
    onChange={(o: ThemeOption) => onChange(o.value as string)}
    options={themeOptions}
  />
);

export default ThemePicker;
