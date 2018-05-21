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

import { connect } from 'react-redux'

import LineContext from './LineContext';

import {
    lineContainerCreated,
    lineContainerDestroyed
} from './redux';

const endpointCalculators = {
    // midway points on edges
    leftCentre : (r) => ({
        x : r.left,
        y : r.top + (r.height / 2)
    }),
    rightCentre : (r) => ({
        x : r.right,
        y : r.top + (r.height / 2)
    }),
    topCentre : (r) => ({
        x : r.left + (r.width / 2),
        y : r.top
    }),
    bottomCentre : (r) => ({
        x : r.left + (r.width / 2),
        y : r.bottom
    }),
    // The corners
    bottomLeft : (r) => ({
        x : r.left,
        y : r.bottom
    }),
    bottomRight : (r) => ({
        x : r.right,
        y : r.bottom
    }),
    topLeft : (r) => ({
        x : r.left,
        y : r.top
    }),
    topRight : (r) => ({
        x : r.right,
        y : r.top
    })
}

const straightLineCreator = ({lineId, fromRect, toRect}) => {
    return (
        <line key={lineId} 
            x1={fromRect.right} y1={fromRect.bottom}
            x2={toRect.left} y2={toRect.top}
            style={{
                stroke:'black',
                strokeWidth: 2,
                fill: 'none'
            }}
            />
    )
}

function calculateLine(k) {
    let lineId = k[0];
    let lineData = k[1];

    const fromElement = document.getElementById(lineData.fromId);
    const toElement = document.getElementById(lineData.toId);

    let fromRect = fromElement.getBoundingClientRect();
    let toRect = toElement.getBoundingClientRect();

    return {
        lineId,
        fromRect,
        toRect
    }
}

class LineContainer extends Component {
    static propTypes = {
        lineContextId : PropTypes.string.isRequired,
        lineElementCreator : PropTypes.func.isRequired // ({lineId, fromRect, toRect})
    }

    static defaultProps = {
        lineElementCreator : straightLineCreator
    }

    state = {
        lines : []
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        let lineContainerState = nextProps.lineContainer[nextProps.lineContextId];
        let lines = [];

        const thisElement = document.getElementById(nextProps.lineContextId);
        let thisRect;
        if (thisElement) {
            thisRect = thisElement.getBoundingClientRect();
        }

        if (!!lineContainerState) {
            lines = Object
                .entries(lineContainerState)
                .map(k => calculateLine(k));
        }

        return {lines, thisRect}
    }

    componentDidMount() {
        lineContainerCreated(this.props.lineContextId);
    }
    
    componentWillUnmount() {
        lineContainerDestroyed(this.props.lineContextId);
    }

    renderChildren() {
        return this.state.lines.map(l => {
            return this.props.lineElementCreator(l);
        })
    }

    render () {
        // If the SVG has been scrolled, we need to translate the generated lines to cancel out that effect
        let transform
        if (this.state.thisRect) {
            transform = 'translate(' + (this.state.thisRect.x * -1) + ', ' + (this.state.thisRect.y * -1) + ')';
        }
        return (
            <LineContext.Provider value={this.props.lineContextId}>
                <div>
                    <svg 
                        id={this.props.lineContextId} 
                        width='1000px'
                        height='1000px'
                        style={{
                            'pointerEvents': 'none',
                            'position':'absolute',
                            'top':0,
                            'left':0
                        }}>
                        <g transform={transform}>
                            {this.renderChildren()}
                        </g>
                    </svg>
                    {this.props.children}
                </div>
            </LineContext.Provider>
        );
    }
}

export default connect(
    (state) => ({
        // operators are nested, so take all their props from parent
        lineContainer : state.lineContainer
    }),
    {
        lineContainerCreated,
        lineContainerDestroyed
    }
)(LineContainer);