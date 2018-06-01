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
import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';

import LineContext from './LineContext';

import { mapObject } from 'lib/treeUtils';

import { lineContainerCreated, lineContainerDestroyed } from './redux';

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

class LineContainer extends Component {
  static propTypes = {
    lineContextId: PropTypes.string.isRequired,
    lineElementCreators: PropTypes.object.isRequired, // {'someLineType': ({lineId, fromRect, toRect}) => (<div>)}
  };

  static defaultProps = {
    lineElementCreators: {
      [DEFAULT_LINE_TYPE]: straightLineCreator,
    },
  };

  state = {
    lines: [],
  };

  static getDerivedStateFromProps(nextProps, prevState) {
    const lineContainerState = nextProps.lineContainer[nextProps.lineContextId];
    let lines = [];

    const thisElement = document.getElementById(nextProps.lineContextId);
    let thisRect;
    if (thisElement) {
      thisRect = thisElement.getBoundingClientRect();
    }

    if (lineContainerState) {
      lines = Object.entries(lineContainerState).map(k => calculateLine(k));
    }

    return { lines, thisRect };
  }

  componentDidMount() {
    lineContainerCreated(this.props.lineContextId);
  }

  componentWillUnmount() {
    lineContainerDestroyed(this.props.lineContextId);
  }

  renderChildren() {
    return this.state.lines.map((l) => {
      const lt = l.lineType || DEFAULT_LINE_TYPE;
      const ltf = this.props.lineElementCreators[lt];
      if (!ltf) {
        return <text key={l.lineId}>Invalid line type for known creators {lt}</text>;
      }
      return ltf(l, this.setLineRef);
    });
  }

  render() {
    // If the SVG has been scrolled, we need to translate the generated lines to cancel out that effect
    let transform;
    if (this.state.thisRect) {
      transform = `translate(${this.state.thisRect.x * -1}, ${this.state.thisRect.y * -1})`;
    }
    return (
      <LineContext.Provider value={this.props.lineContextId}>
        <div className={this.props.className}>
          <svg
            id={this.props.lineContextId}
            width="1000px"
            height="1000px"
            style={{
              pointerEvents: 'none',
              position: 'absolute',
              top: 0,
              left: 0,
            }}
          >
            <g transform={transform}>{this.renderChildren()}</g>
          </svg>
          {this.props.children}
        </div>
      </LineContext.Provider>
    );
  }
}

export default connect(
  state => ({
    // operators are nested, so take all their props from parent
    lineContainer: state.lineContainer,
  }),
  {
    lineContainerCreated,
    lineContainerDestroyed,
  },
)(LineContainer);
