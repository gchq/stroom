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

import LineContext from './LineContext';

export const domRectBoundCalcs = {
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
    bottomLeft : (r) => ({
        x : r.left,
        y : r.bottom
    })
}

class LineTo extends Component {
    static propTypes = {
        // These are the id's of the endpoint elements.
        fromId : PropTypes.string.isRequired,
        toId : PropTypes.string.isRequired,

        // These need to be functions that accept a domRect and return appropriate start/end points
        calculateStart : PropTypes.func.isRequired,
        calculateEnd : PropTypes.func.isRequired
    }

    static defaultProps = {
        calculateStart : domRectBoundCalcs.rightCentre,
        calculateEnd : domRectBoundCalcs.leftCentre
    }

    componentDidMount() {
        const canvas = document.getElementById(this.props.lineContextId);
        const fromElement = document.getElementById(this.props.fromId);
        const toElement = document.getElementById(this.props.toId);

        let fromRect = fromElement.getBoundingClientRect();
        let toRect = toElement.getBoundingClientRect();

        let fromPosition = this.props.calculateStart(fromRect);
        let toPosition = this.props.calculateEnd(toRect);

        console.log('From Position', fromPosition);
        console.log('To Position', toPosition);

        const ctx = canvas.getContext("2d");
        ctx.beginPath();
        ctx.moveTo(fromPosition.x, fromPosition.y);
        ctx.lineTo(toPosition.x, toPosition.y);
        ctx.stroke();
    }

    render() {
        return (
            <span></span>
        )
    }
}

export default props => (
    <LineContext.Consumer>
        {lineContextId => <LineTo {...props} lineContextId={lineContextId} />}
    </LineContext.Consumer>
  );