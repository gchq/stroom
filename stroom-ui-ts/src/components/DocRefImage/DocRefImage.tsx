import * as React from "react";

export interface Props {
  docRefType: string;
  size: "lg" | "sm";
  className?: string;
}

const DocRefImage = ({ docRefType, size = "lg", className = "" }: Props) => (
  <img
    className={`stroom-icon--${size} ${className}`}
    alt={`doc ref icon ${docRefType}`}
    src={require(`../../images/docRefTypes/${docRefType}.svg`)}
  />
);

export default DocRefImage;
