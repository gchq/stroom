import { FunctionComponent, useState } from "react";
import { TextBox } from "components/FormField";
import { Search } from "react-bootstrap-icons";
import * as React from "react";

export interface QuickFilterProps {
  initialValue?: string;
  onChange?: (value: string) => void;
}

export const QuickFilter: FunctionComponent<QuickFilterProps> = ({
  initialValue,
  onChange,
}) => {
  const [value, setValue] = useState(initialValue);
  return (
    <TextBox
      controlId="quickFilter"
      type="text"
      className="QuickFilter left-icon-padding hide-background-image"
      placeholder="Quick Filter"
      value={value}
      onChange={(e) => {
        setValue(e.target.value);
        onChange && onChange(e.target.value);
      }}
      autoFocus={true}
    >
      <Search className="FormField__icon" />
    </TextBox>
  );
};
