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

import { SizeProp } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import * as React from "react";
import { ButtonProps } from "./types";
import RippleContainer, { useRipple } from "./RippleContainer";
import { forwardRef, ForwardRefRenderFunction, useEffect, useRef } from "react";
import { Spinner } from "react-bootstrap";

export const Button: ForwardRefRenderFunction<
  HTMLButtonElement,
  ButtonProps
> = ({
  icon,
  className: rawClassName,
  appearance,
  action,
  selected,
  disabled,
  loading,
  size,
  onClick,
  children,
  autoFocus = false,
  ...rest
}: ButtonProps) => {
  const className = React.useMemo(() => {
    const classNames = ["Button"];

    if (rawClassName) classNames.push(rawClassName);

    // Set the base button class.
    classNames.push("Button--base");
    // Set the general button styling class unless this is an icon button.
    if (appearance !== "icon") classNames.push("Button");

    // Get the style name.
    let appearanceName = "Button--default";
    if (appearance) {
      switch (appearance) {
        case "default": {
          appearanceName = "Button--default";
          break;
        }
        case "outline": {
          appearanceName = "Button--outline";
          break;
        }
        case "icon": {
          appearanceName = "Button--icon";
          break;
        }
        case "contained": {
          appearanceName = "Button--contained";
          break;
        }
        default:
          break;
      }
    }
    // Set the style name.
    classNames.push(appearanceName);

    // Get the color (none by default);
    let actionName;
    if (action) {
      switch (action) {
        case "primary": {
          actionName = appearanceName + "-primary";
          break;
        }
        case "secondary": {
          actionName = appearanceName + "-secondary";
          break;
        }
        default:
          break;
      }
    }
    if (actionName) {
      // Set the color.
      classNames.push(actionName);
    }

    if (children) classNames.push("has-text");
    if (selected) classNames.push("Button--selected");
    if (disabled) classNames.push("Button--disabled");
    if (loading) classNames.push("Button--loading");

    classNames.push(size);

    return classNames.join(" ");
  }, [
    rawClassName,
    appearance,
    action,
    children,
    selected,
    disabled,
    loading,
    size,
  ]);

  const fontAwesomeSize: SizeProp = React.useMemo(() => {
    switch (size) {
      case "small":
        return "sm";
      case "medium":
        return "1x";
      case "large":
        return "lg";
      case "xlarge":
        return "2x";
      // default:
      //   return "1x";
    }
    return "lg";
  }, [size]);

  const showText = children && appearance !== "icon";

  const { onClickWithRipple, ripples } = useRipple(onClick);

  // For some reason autofocus doesn't work inside bootstrap modal forms so we need to use an effect.
  const element = useRef(null);
  useEffect(() => {
    if (autoFocus) {
      element.current.focus();
    }
  }, [autoFocus]);

  return (
    <button
      className={className}
      onClick={onClickWithRipple}
      ref={element}
      {...rest}
    >
      <RippleContainer ripples={ripples} />
      <div className="Button__content">
        <span
          className={
            loading
              ? "Button__spinner Button__spinner-loading"
              : "Button__spinner"
          }
        >
          <Spinner
            as="span"
            animation="border"
            size="sm"
            role="status"
            aria-hidden="true"
          />
        </span>

        {icon ? (
          <FontAwesomeIcon size={fontAwesomeSize} icon={icon} />
        ) : undefined}
        {showText && icon ? <span className="Button__margin" /> : undefined}

        {showText ? (
          <span className="Button__text">{children}</span>
        ) : undefined}
      </div>
    </button>
  );
};

export default forwardRef(Button);
