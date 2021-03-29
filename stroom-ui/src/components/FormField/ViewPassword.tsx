import * as React from "react";
import { FunctionComponent } from "react";

import { Eye, EyeSlash } from "react-bootstrap-icons";

export interface ViewPasswordProps {
  state: boolean;
  onStateChanged?: (state: boolean) => void;
}

export const ViewPassword: FunctionComponent<ViewPasswordProps> = ({
  state,
  onStateChanged = (s: boolean) => s,
}) => {
  return (
    <React.Fragment>
      {state ? (
        <EyeSlash
          className="ViewPassword__icon"
          onClick={() => onStateChanged(!state)}
        />
      ) : (
        <Eye
          className="ViewPassword__icon"
          onClick={() => onStateChanged(!state)}
        />
      )}
    </React.Fragment>
  );
};
