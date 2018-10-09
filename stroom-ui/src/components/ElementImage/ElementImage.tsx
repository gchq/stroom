import * as React from "react";

export interface Props {
  icon: string;
  size?: "lg" | "sm";
  className?: string;
}

const ElementImage = ({ icon, size = "lg", className = "" }: Props) => (
  <img
    className={`stroom-icon--${size} ${className}`}
    alt={`element icon ${icon}`}
    src={require(`../../images/elements/${icon}`)}
  />
);

export default ElementImage;
