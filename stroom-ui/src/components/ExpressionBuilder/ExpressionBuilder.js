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

import ExpressionOperator from './ExpressionOperator';
import { LineContainer } from 'components/LineTo'

import { withDataSource } from 'components/DataSource';
import { withExpression } from './withExpression';

import './ExpressionBuilder.css'

const defaultExpression = {
    "uuid" : "root",
    "type" : "operator",
    "op" : "AND",
    "children": [],
    "enabled" : true
};

const downRightElbow = ({lineId, fromRect, toRect}) => {
    let from = {
        x : fromRect.left + (fromRect.width / 2) - 2,
        y : fromRect.bottom
    };
    let to = {
        x : toRect.left,
        y : toRect.top + (toRect.height / 2)
    };
    let pathSpec = 'M ' + from.x + ' ' + from.y
                + ' L ' + from.x + ' ' + to.y
                + ' L ' + to.x + ' ' + to.y;
    return (
        <path key={lineId}  d={pathSpec} style={{
            stroke:'grey',
            strokeWidth: 2,
            fill: 'none'
        }} />
    )
}

let lineElementCreators = {
    'downRightElbow' : downRightElbow
}

const ExpressionBuilder = ({expressionId, dataSource, expression}) => (
    <LineContainer 
        lineContextId={'expression-lines-' + expressionId}
        lineElementCreators={lineElementCreators}
        >
        <ExpressionOperator 
            dataSource={dataSource}
            expressionId={expressionId}
            isRoot={true}
            isEnabled={true}
            operator={expression}  />
    </LineContainer>
)

ExpressionBuilder.propTypes = {
    dataSource : PropTypes.object.isRequired,
    expressionId : PropTypes.string.isRequired,
    expression: PropTypes.object.isRequired, // expects the entire map of expressions to be available
}

export default withDataSource(withExpression(ExpressionBuilder));

