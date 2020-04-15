import * as React from "react";

import { storiesOf } from "@storybook/react";
import useOnKeyDown from "./useOnKeyDown";

interface Location {
  x: number;
  y: number;
}
const MAX_DIM = 10;
const LOCATIONS: Location[][] = [];

const colStyle: React.CSSProperties = {
  width: "100px",
  height: "10px",
  display: "flex",
  flexDirection: "row",
};
const cellStyle: React.CSSProperties = {
  width: "10px",
  display: "flex",
  flexDirection: "column",
  border: "solid thin black",
};

for (let y = 0; y < MAX_DIM; y++) {
  let thisRow: Location[] = [];
  for (let x = 0; x < MAX_DIM; x++) {
    thisRow.push({ x, y });
  }
  LOCATIONS.push(thisRow);
}

const reducer = (
  { x, y }: Location,
  action: "up" | "down" | "left" | "right",
) => {
  switch (action) {
    case "up":
      return {
        x,
        y: (y - 1 + MAX_DIM) % MAX_DIM,
      };
    case "down":
      return {
        x,
        y: (y + 1) % MAX_DIM,
      };
    case "left":
      return {
        x: (x - 1 + MAX_DIM) % MAX_DIM,
        y,
      };
    case "right":
      return {
        x: (x + 1) % MAX_DIM,
        y,
      };
    default:
      return { x, y };
  }
};

const TestHarness: React.FunctionComponent = () => {
  const [{ x, y }, dispatch] = React.useReducer(reducer, { x: 0, y: 0 });

  const goUp = React.useCallback(() => dispatch("up"), [dispatch]);
  const goDown = React.useCallback(() => dispatch("down"), [dispatch]);
  const goLeft = React.useCallback(() => dispatch("left"), [dispatch]);
  const goRight = React.useCallback(() => dispatch("right"), [dispatch]);

  const onKeyDown = useOnKeyDown({
    ArrowUp: goUp,
    ArrowDown: goDown,
    ArrowLeft: goLeft,
    ArrowRight: goRight,
  });

  const mainDiv = React.useCallback(node => {
    if (node != null) {
      node.focus();
    }
  }, []);

  return (
    <div>
      <h1>Demonstrates Key Handling</h1>
      <p>
        Press the arrow keys to move the square around. This test uses a reducer
        with a location state to track the position of the red square.
      </p>
      <div ref={mainDiv} tabIndex={0} onKeyDown={onKeyDown}>
        {LOCATIONS.map((col, i) => (
          <div key={i} style={colStyle}>
            {col.map(({ x: cX, y: cY }, j) => {
              let backgroundColor = "white";
              if (x === cX && y === cY) {
                backgroundColor = "red";
              }
              return <div key={j} style={{ ...cellStyle, backgroundColor }} />;
            })}
          </div>
        ))}
        Last Key Pressed: {x}, {y}
      </div>
    </div>
  );
};

storiesOf("lib/useOnKeyDown", module).add("arrows", () => <TestHarness />);
