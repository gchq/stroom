import * as React from "react";
import { FunctionComponent } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { IconProp } from "@fortawesome/fontawesome-svg-core";

export interface ViewPasswordProps {
  state: boolean;
  onStateChanged?: (state: boolean) => void;
}

const ViewPassword: FunctionComponent<ViewPasswordProps> = ({
  state,
  onStateChanged = (s: boolean) => s,
}) => {
  const icon: IconProp = state ? "eye" : "eye-slash";
  return (
    <div className="ViewPassword">
      <FontAwesomeIcon
        className="ViewPassword__icon"
        size="lg"
        icon={icon}
        onClick={() => onStateChanged(!state)}
      />
    </div>
  );
};

export default ViewPassword;
