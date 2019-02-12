import * as React from "react";

import { DropdownOptionProps } from "./DropdownSelect";

interface WithProps {
  className: string;
}

export interface EnhancedProps extends DropdownOptionProps, WithProps {}

export const DefaultDropdownOption = ({
  option,
  className,
  onClick
}: EnhancedProps) => (
  <div className={className} onClick={onClick}>
    {option.text}
  </div>
);

let DefaultDropdownOption2 = ({
  option,
  inFocus,
  onClick
}: DropdownOptionProps) => {
  let classNames = ["hoverable"];

  if (inFocus) classNames.push("inFocus");

  const className = classNames.join(" ");

  return (
    <div className={className} onClick={onClick}>
      {option.text}
    </div>
  );
};
export default DefaultDropdownOption2;
