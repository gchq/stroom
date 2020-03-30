import * as React from "react";

import { LineDefinition, LineElementCreator } from "../types";

const ElbowDown: LineElementCreator = ({
  fromRect,
  toRect,
}: LineDefinition) => (
  <React.Fragment>
    <line
      x1={fromRect.left + fromRect.width / 2}
      y1={fromRect.bottom}
      x2={fromRect.left + fromRect.width / 2}
      y2={toRect.top + toRect.height / 2}
      style={{
        stroke: "black",
        strokeWidth: 1,
        fill: "none",
      }}
    />
    <line
      x1={fromRect.left + fromRect.width / 2}
      y1={toRect.top + toRect.height / 2}
      x2={toRect.left}
      y2={toRect.top + toRect.height / 2}
      style={{
        stroke: "black",
        strokeWidth: 1,
        fill: "none",
      }}
    />
  </React.Fragment>
);

export default ElbowDown;
