import * as React from "react";

interface Props {
  docRefType: string;
  size?: "lg" | "sm";
  className?: string;
}

const DocRefImage: React.FunctionComponent<Props> = ({
  docRefType,
  size = "lg",
  className = "",
}) => {
  if (docRefType !== "NONE") {
    return (
      <img
        className={`stroom-icon--${size} ${className || ""}`}
        alt={`doc ref icon ${docRefType}`}
        src={require(`../../images/docRefTypes/${docRefType}.svg`)}
      />
    );
  } else {
    return null;
  }
};

export default DocRefImage;
