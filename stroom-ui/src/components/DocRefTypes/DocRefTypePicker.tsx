import * as React from "react";

import DocRefImage from "../DocRefImage";
import { OptionType } from "../../types";
import DropdownSelect, { DropdownOptionProps } from "../DropdownSelect";

import withDocRefTypes, {
  EnhancedProps as WithDocRefTypeProps
} from "./withDocRefTypes";

const DocRefTypeOption = ({
  inFocus,
  option: { text, value },
  onClick
}: DropdownOptionProps) => (
  <div className={`hoverable ${inFocus ? "inFocus" : ""}`} onClick={onClick}>
    <DocRefImage size="sm" docRefType={value} />
    {text}
  </div>
);

export interface Props {
  onChange: (docRefType: string) => any;
  value: string;
}

export interface EnhancedProps extends Props, WithDocRefTypeProps {}

let DocRefTypePicker = ({ docRefTypes, ...rest }: EnhancedProps) => {
  let options: Array<OptionType> = docRefTypes.map((d: string) => ({
    text: d,
    value: d
  }));
  return (
    <DropdownSelect
      {...rest}
      options={options}
      OptionComponent={DocRefTypeOption}
    />
  );
};

export default withDocRefTypes<Props>()(DocRefTypePicker);
