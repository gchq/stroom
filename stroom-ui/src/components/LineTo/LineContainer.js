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

import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle, withProps, branch, renderComponent } from 'recompose';

import Loader from 'components/Loader'
import LineContext from './LineContext';
import { actionCreators } from './redux';

const { lineContainerCreated, lineContainerDestroyed } = actionCreators;

/**
 * This function is the default line creation function.
 * It shows how such a function can be written. The first parameter is an object that must contain
 * the following
 * { lineId, fromRect, toRect}
 *
 * @param {{ lineId, fromRect, toRect }} line details
 */
const straightLineCreator = ({ lineId, fromRect, toRect }) => (
  <line
    key={lineId}
    x1={fromRect.left + fromRect.width / 2}
    y1={fromRect.top + fromRect.height / 2}
    x2={toRect.left + toRect.height / 2}
    y2={toRect.top + toRect.height / 2}
    style={{
      stroke: 'black',
      strokeWidth: 2,
      fill: 'none',
    }}
  />
);

function calculateLine(k) {
  const lineId = k[0];
  const lineData = k[1];

  const lineType = lineData.lineType;
  const fromElement = document.getElementById(lineData.fromId);
  const toElement = document.getElementById(lineData.toId);

  const fromRect = fromElement.getBoundingClientRect();
  const toRect = toElement.getBoundingClientRect();

  return {
    lineId,
    lineType,
    fromRect,
    toRect,
  };
}

const DEFAULT_LINE_TYPE = 'straight-line';

const enhance = compose(
  connect(
    (state, props) => ({
      // operators are nested, so take all their props from parent
      lineContainer: state.lineContainer[props.lineContextId],
    }),
    {
      lineContainerCreated,
      lineContainerDestroyed,
    },
  ),
  lifecycle({
    componentDidMount() {
      this.props.lineContainerCreated(this.props.lineContextId);
    },

    componentWillUnmount() {
      this.props.lineContainerDestroyed(this.props.lineContextId);
    },
  }),
  branch(
    ({ lineContainer }) => !lineContainer,
    renderComponent(() => <Loader message="Loading pipeline..." />),
  ),
  withProps(({ lineContextId, lineContainer }) => {
    let lines = [];

    const thisElement = document.getElementById(lineContextId);
    let thisRect;
    if (thisElement) {
      thisRect = thisElement.getBoundingClientRect();
    }

    if (lineContainer) {
      lines = Object.entries(lineContainer).map(k => calculateLine(k));
    }

    return { lines, thisRect };
  }),
);

const LineContainer = ({
  lineContextId,
  lineElementCreators,
  className,
  thisRect,
  lines,
  children,
}) => {
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
            {lines.map((l) => {
              const lt = l.lineType || DEFAULT_LINE_TYPE;
              const ltf = lineElementCreators[lt];
              if (!ltf) {
                return <text key={l.lineId}>Invalid line type for known creators {lt}</text>;
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

const EnhancedLineContainer = enhance(LineContainer);

EnhancedLineContainer.propTypes = {
  lineContextId: PropTypes.string.isRequired,
  lineElementCreators: PropTypes.object.isRequired, // {'someLineType': ({lineId, fromRect, toRect}) => (<div>)}
  className: PropTypes.string,
};

EnhancedLineContainer.defaultProps = {
  lineElementCreators: {
    [DEFAULT_LINE_TYPE]: straightLineCreator,
  },
};

export default EnhancedLineContainer;