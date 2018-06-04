import React from 'react';

const curve = ({ lineId, fromRect, toRect }) => {
  const from = {
    x: fromRect.right,
    y: fromRect.top + fromRect.height / 3, // bit of a fiddle to get into the middle
  };
  const to = {
    x: toRect.left,
    y: toRect.top + toRect.height / 3,
  };

  // if they are inline with eachother, draw a straight line
  if (fromRect.top === toRect.top) {
    const pathSpec = `M ${from.x} ${from.y} L ${to.x} ${to.y}`;
    return (
      <path
        key={lineId}
        d={pathSpec}
        style={{
          stroke: 'black',
          strokeWidth: 2,
          fill: 'none',
        }}
      />
    );
  }
  // otherwise draw a curve
  const mid = {
    x: from.x + (to.x - from.x) / 2,
    y: from.y + (to.y - from.y) / 2,
  };

  const pathSpec = `M ${from.x} ${from.y} C ${from.x} ${from.y} ${mid.x} ${from.y} ${mid.x} ${
    mid.y
  } C ${mid.x} ${mid.y} ${mid.x} ${to.y} ${to.x} ${to.y}`;
  return (
    <path
      key={lineId}
      d={pathSpec}
      style={{
        stroke: 'black',
        strokeWidth: 2,
        fill: 'none',
      }}
    />
  );
};

export default {
  curve,
};
