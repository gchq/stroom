import * as React from "react";
import { LineElementCreator, LineDefinition } from "../types";

const Curve: LineElementCreator = ({ fromRect, toRect }: LineDefinition) => {
  let from = {
    x: fromRect.left + fromRect.width / 2,
    y: fromRect.bottom,
  };
  let to = {
    x: toRect.left,
    y: toRect.top + toRect.height / 2,
  };
  let pathSpec =
    "M " +
    from.x +
    " " +
    from.y +
    " C " +
    from.x +
    " " +
    from.y +
    " " +
    from.x +
    " " +
    to.y +
    " " +
    to.x +
    " " +
    to.y;
  return (
    <path
      d={pathSpec}
      style={{
        stroke: "black",
        strokeWidth: 1,
        fill: "none",
      }}
    />
  );
};

export default Curve;
