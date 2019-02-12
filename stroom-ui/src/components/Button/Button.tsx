/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { IconProp, SizeProp } from "@fortawesome/fontawesome-svg-core";

export type IconSize = "small" | "medium" | "large" | "xlarge";

/**
 * Button Properties
 */
export interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** The Font Awesome Icon to use */
  icon?: IconProp;
  /** Indicates how to style the button within a group (left | middle | right)  */
  groupPosition?: string;
  /** Make the button circular */
  circular?: boolean;
  /** Place this text on the button */
  text?: string;
  /** Indicate the button has been selected */
  selected?: boolean;
  /** Custom additional class to apply to the button */
  className?: string;
  /** The size of the icon*/
  size?: IconSize;
}

export const Button = ({
  text,
  icon,
  className: rawClassName,
  groupPosition,
  circular,
  selected,
  size,
  ...rest
}: Props) => {
  let classNames = ["button"];

  if (rawClassName) classNames.push(rawClassName);
  if (groupPosition) classNames.push(groupPosition);
  if (circular) classNames.push("circular");
  if (text) classNames.push("has-text");
  if (selected) classNames.push("selected");

  let fontAwesomeSize: SizeProp;
  switch (size) {
    case "small":
      fontAwesomeSize = "sm";
      break;
    case "medium":
      fontAwesomeSize = "1x";
      break;
    case "large":
      fontAwesomeSize = "lg";
      break;
    case "xlarge":
      fontAwesomeSize = "2x";
      break;
    default:
      fontAwesomeSize = "1x";
  }
  const className = classNames.join(" ");

  return (
    <button className={className} {...rest}>
      {icon ? (
        <FontAwesomeIcon size={fontAwesomeSize} icon={icon} />
      ) : (
        undefined
      )}
      {text ? <span className="button__text">{text}</span> : undefined}
    </button>
  );
};

export default Button;
