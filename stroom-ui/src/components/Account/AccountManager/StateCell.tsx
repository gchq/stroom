import * as React from "react";
import { RowInfo } from "react-table";

const StateCell: React.FunctionComponent<RowInfo> = ({ row: { state } }) => {
  let stateColour, stateText;
  switch (state) {
    case "enabled":
      stateColour = "#57d500";
      stateText = "Active";
      break;
    case "locked":
      stateColour = "#ff2e00";
      stateText = "Locked";
      break;
    case "disabled":
      stateColour = "#ff2e00";
      stateText = "Disabled";
      break;
    case "inactive":
      stateColour = "#ff2e00";
      stateText = "Inactive";
      break;
    default:
      stateColour = "#ffbf00";
      stateText = "Unknown!";
  }
  return (
    <span>
      <span
        style={{
          color: stateColour,
          transition: "all .3s ease",
        }}
      >
        &#x25cf;
      </span>{" "}
      {stateText}
    </span>
  );
};

export default StateCell;
