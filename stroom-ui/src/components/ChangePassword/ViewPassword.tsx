import * as React from "react";
import { FunctionComponent } from "react";
import { EyeOutlined, EyeInvisibleOutlined } from "@ant-design/icons";

export interface ViewPasswordProps {
  state: boolean;
  onStateChanged?: (state: boolean) => void;
}

const ViewPassword: FunctionComponent<ViewPasswordProps> = ({
  state,
  onStateChanged = (s: boolean) => s,
}) => {
  return (
    <div className="ViewPassword__icon-container">
      {state ? (
        <EyeInvisibleOutlined
          className="ViewPassword__icon"
          onClick={() => onStateChanged(!state)}
        />
      ) : (
        <EyeOutlined
          className="ViewPassword__icon"
          onClick={() => onStateChanged(!state)}
        />
      )}
    </div>
  );
};

export default ViewPassword;
