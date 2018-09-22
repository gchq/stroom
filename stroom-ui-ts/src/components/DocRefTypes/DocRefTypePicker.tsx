import * as React from "react";
import { compose, withProps } from "recompose";

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
  pickerId: string;
  onChange: (docRefType: string) => any;
  value: string;
}

export interface AddedProps {
  options: Array<OptionType>;
}

export interface EnhancedProps extends Props, WithDocRefTypeProps, AddedProps {}

const enhance = compose<EnhancedProps, Props>(
  withDocRefTypes,
  withProps(({ docRefTypes }) => ({
    options: docRefTypes.map((d: string) => ({ text: d, value: d }))
  }))
);

let DocRefTypePicker = (props: EnhancedProps) => (
  <DropdownSelect {...props} OptionComponent={DocRefTypeOption} />
);

export default enhance(DocRefTypePicker);
