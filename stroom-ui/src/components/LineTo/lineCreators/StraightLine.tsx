import * as React from "react";

import { LineDefinition, LineElementCreator } from "../types";

const StraightLine: LineElementCreator = ({
  fromRect,
  toRect,
}: LineDefinition) => (
  <line
    x1={fromRect.left + fromRect.width / 2}
    y1={fromRect.top + fromRect.height / 2}
    x2={toRect.left + toRect.height / 2}
    y2={toRect.top + toRect.height / 2}
    style={{
      stroke: "black",
      strokeWidth: 1,
      fill: "none",
    }}
  />
);

export default StraightLine;
