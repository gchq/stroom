import * as React from "react";

import { LineType, LineElementCreator, LineDefinition } from "./types";
import useInterval from "lib/useInterval";
import LineContext from "./LineContext";

interface Props {
  LineElementCreator: LineElementCreator;
}

function calculateLine({ fromId, toId }: LineType): LineDefinition | undefined {
  const fromElement = document.getElementById(fromId);
  const toElement = document.getElementById(toId);

  if (fromElement && toElement) {
    const fromRect = fromElement.getBoundingClientRect() as DOMRect;
    const toRect = toElement.getBoundingClientRect() as DOMRect;

    return {
      lineId: `${fromId}-${toId}`,
      fromRect,
      toRect,
    };
  } else {
    return undefined;
  }
}

export const useRefreshCounter = () => {
  const [count, setCount] = React.useState(0);

  useInterval({
    callback: () => {
      // Your custom logic here
      setCount(count + 1);
    },
    delay: 1000,
  });

  return count;
};

const LinesSvg: React.FunctionComponent<Props> = ({ LineElementCreator }) => {
  const { rawLines, lineContextId, getEndpointId } = React.useContext(
    LineContext,
  );

  const thisRect: DOMRect | undefined = React.useMemo(() => {
    const thisElement = document.getElementById(lineContextId);
    return thisElement
      ? (thisElement.getBoundingClientRect() as DOMRect)
      : undefined;
  }, [lineContextId]);

  //const refresh = useRefreshCounter();
  const lines: LineDefinition[] = React.useMemo(
    () =>
      rawLines
        .map(({ lineId, fromId, toId }) => ({
          lineId,
          fromId: getEndpointId(fromId),
          toId: getEndpointId(toId),
        }))
        .map(calculateLine)
        .filter((e) => e !== undefined)
        .map((e) => e as LineDefinition),
    [rawLines, getEndpointId],
  );

  // If the SVG has been scrolled, we need to translate the generated lines to cancel out that effect
  let transform;
  if (thisRect) {
    transform = `translate(${thisRect.x * -1}, ${thisRect.y * -1})`;
  }

  return (
    <svg className="LineContainer-svg" id={lineContextId}>
      <g transform={transform}>
        {lines.map((l) => (
          <LineElementCreator key={l.lineId} {...l} />
        ))}
      </g>
    </svg>
  );
};

export default LinesSvg;
