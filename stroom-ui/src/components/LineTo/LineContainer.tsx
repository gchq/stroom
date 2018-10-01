/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import { connect } from "react-redux";
import {
  compose,
  lifecycle,
  withProps,
  branch,
  renderComponent
} from "recompose";

import { GlobalStoreState } from "../../startup/reducers";
import Loader from "../Loader";
import LineContext from "./LineContext";
import { actionCreators, StoreStatePerId } from "./redux";
import {
  LineType,
  LineDefinition,
  LineElementCreators,
  LineElementCreator
} from "./types";

const { lineContainerCreated, lineContainerDestroyed } = actionCreators;

/**
 * This function is the default line creation function.
 * It shows how such a function can be written. The first parameter is an object that must contain
 * the following
 * { lineId, fromRect, toRect}
 *
 * @param {{ lineId, fromRect, toRect }} line details
 */
const straightLineCreator: LineElementCreator = ({
  lineId,
  fromRect,
  toRect
}: LineDefinition) => (
  <line
    key={lineId}
    x1={fromRect.left + fromRect.width / 2}
    y1={fromRect.top + fromRect.height / 2}
    x2={toRect.left + toRect.height / 2}
    y2={toRect.top + toRect.height / 2}
    style={{
      stroke: "black",
      strokeWidth: 2,
      fill: "none"
    }}
  />
);

function calculateLine(
  lineId: string,
  lineData: LineType
): LineDefinition | undefined {
  const lineType = lineData.lineType;
  const fromElement = document.getElementById(lineData.fromId);
  const toElement = document.getElementById(lineData.toId);

  if (fromElement && toElement) {
    const fromRect = fromElement.getBoundingClientRect() as DOMRect;
    const toRect = toElement.getBoundingClientRect() as DOMRect;

    return {
      lineId,
      lineType,
      fromRect,
      toRect
    };
  } else {
    return undefined;
  }
}

const DEFAULT_LINE_TYPE = "straight-line";

export interface Props {
  lineContextId: string;
  className?: string;
  children: React.ReactNode;
  lineElementCreators?: LineElementCreators;
}

interface ConnectState {
  lineContainer: StoreStatePerId;
}

interface ConnectDispatch {
  lineContainerCreated: typeof lineContainerCreated;
  lineContainerDestroyed: typeof lineContainerDestroyed;
}

interface WithProps {
  lines: Array<LineDefinition>;
  thisRect: DOMRect;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ lineContainer }, { lineContextId }) => ({
      lineContainer: lineContainer.byId[lineContextId]
    }),
    {
      lineContainerCreated,
      lineContainerDestroyed
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}, {}>({
    componentDidMount() {
      const { lineContainerCreated, lineContextId } = this.props;
      lineContainerCreated(lineContextId);
    },

    componentWillUnmount() {
      const { lineContainerDestroyed, lineContextId } = this.props;
      lineContainerDestroyed(lineContextId);
    }
  }),
  branch(
    ({ lineContainer }) => !lineContainer,
    renderComponent(() => <Loader message="Loading Line Container..." />)
  ),
  withProps(({ lineContextId, lineContainer }) => {
    let lines: Array<LineDefinition> = [];

    const thisElement = document.getElementById(lineContextId);
    let thisRect;
    if (thisElement) {
      thisRect = thisElement.getBoundingClientRect();
    }

    if (lineContainer) {
      lines = Object.entries(lineContainer)
        .map(k => calculateLine(k[0], k[1] as LineType))
        .filter(e => e !== undefined)
        .map(e => e as LineDefinition);
    }

    return { lines, thisRect };
  })
);

const LineContainer = ({
  lineContextId,
  lineElementCreators = {
    DEFAULT_LINE_TYPE: straightLineCreator
  },
  className,
  thisRect,
  lines,
  children
}: EnhancedProps) => {
  // If the SVG has been scrolled, we need to translate the generated lines to cancel out that effect
  let transform;
  if (thisRect) {
    transform = `translate(${thisRect.x * -1}, ${thisRect.y * -1})`;
  }
  return (
    <LineContext.Provider value={lineContextId}>
      <div className={className}>
        <svg className="LineContainer-svg" id={lineContextId}>
          <g transform={transform}>
            {lines.map(l => {
              const lt = l.lineType || DEFAULT_LINE_TYPE;
              const ltf = lineElementCreators[lt];
              if (!ltf) {
                return (
                  <text key={l.lineId}>
                    Invalid line type for known creators {lt}
                  </text>
                );
              }
              return ltf(l);
            })}
          </g>
        </svg>
        {children}
      </div>
    </LineContext.Provider>
  );
};

export default enhance(LineContainer);
