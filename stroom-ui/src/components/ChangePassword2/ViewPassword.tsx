import * as React from "react";
import { FunctionComponent } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { IconProp, IconPrefix } from "@fortawesome/fontawesome-svg-core";


export interface ViewPasswordProps {
  state: boolean;
  onStateChanged?: (state: boolean) => void;
}

const ViewPassword: FunctionComponent<ViewPasswordProps> = ({
  state,
  onStateChanged = (s: boolean) => s,
}) => {
  const icon: IconProp = state ? "eye-slash" : "eye";
  const prefix: IconPrefix = "far";
  return (
    <div className="ViewPassword__icon-container">
>

      <FontAwesomeIcon
        className="ViewPassword__icon"
        icon={[prefix, icon]}
        onClick={() => onStateChanged(!state)}
      />
    </div>
  );
};

export default ViewPassword;
