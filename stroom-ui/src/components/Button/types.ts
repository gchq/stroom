import { IconProp } from "@fortawesome/fontawesome-svg-core";
import { ButtonHTMLAttributes } from "react";

/**
 * Button Properties
 */
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** The Font Awesome Icon to use */
  icon?: IconProp;
  /** Choose the button appearance */
  appearance?: "default" | "outline" | "icon" | "contained";
  /** Choose the button action */
  action?: "default" | "primary" | "secondary";
  /** Place this text on the button */
  text?: string;
  /** Indicate the button has been selected */
  selected?: boolean;
  /** Indicate the button has been disabled */
  disabled?: boolean;
  /** Custom additional class to apply to the button */
  className?: string;
  /** The size of the icon*/
  size?: "small" | "medium" | "large" | "xlarge";
}
