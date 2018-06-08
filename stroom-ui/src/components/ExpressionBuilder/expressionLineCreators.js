import React from 'react';

const downRightElbow = ({ lineId, fromRect, toRect }) => {
  const from = {
    x: fromRect.left + fromRect.width / 2 - 2,
    y: fromRect.bottom,
  };
  const to = {
    x: toRect.left,
    y: toRect.top + toRect.height / 2,
  };
  const pathSpec = `M ${from.x} ${from.y} L ${from.x} ${to.y} L ${to.x} ${to.y}`;
  return (
    <path
      key={lineId}
      d={pathSpec}
      style={{
        stroke: 'grey',
        strokeWidth: 2,
        fill: 'none',
      }}
    />
  );
};

export default {
  downRightElbow,
};