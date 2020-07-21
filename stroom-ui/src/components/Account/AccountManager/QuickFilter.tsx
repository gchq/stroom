import { FunctionComponent, useState } from "react";
import { TextBox } from "components/FormField";
import { Search } from "react-bootstrap-icons";
import * as React from "react";
import { FormFieldState } from "../../FormField/FormField";

export interface QuickFilterProps {
  initialValue?: string;
  onChange?: (value: string) => void;
}

export const QuickFilter: FunctionComponent<QuickFilterProps> = ({
  initialValue,
  onChange,
}) => {
  const [value, setValue] = useState(initialValue);
  const state: FormFieldState<string> = {
    value,
    onChange: (val) => {
      setValue(val);
      onChange && onChange(val);
    },
  };

  return (
    <TextBox
      type="text"
      className="QuickFilter left-icon-padding hide-background-image"
      placeholder="Quick Filter"
      autoFocus={true}
      state={state}
    >
      <Search className="FormField__icon" />
    </TextBox>
  );
};
