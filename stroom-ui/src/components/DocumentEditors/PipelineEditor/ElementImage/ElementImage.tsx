import * as React from "react";

interface Props {
  icon: string;
  size?: "lg" | "sm";
  className?: string;
}

const ElementImage: React.FunctionComponent<Props> = ({
  icon,
  size = "lg",
  className = "",
}) => (
  <img
    className={`stroom-icon--${size} ${className || ""}`}
    alt={`element icon ${icon}`}
    src={require(`../../../../images/elements/${icon}`)}
  />
);

export default ElementImage;
