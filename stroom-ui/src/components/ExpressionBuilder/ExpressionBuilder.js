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

import ExpressionOperator from './ExpressionOperator';
import ROExpressionOperator from './ROExpressionOperator';
import { LineContainer } from 'components/LineTo'

import { withDataSource } from 'components/DataSource';
import { withExpression } from './withExpression';

import {
    Checkbox
} from 'semantic-ui-react';

import {
    expressionEditorCreated,
    expressionEditorDestroyed,
    expressionSetEditable
} from './redux';

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

class ExpressionBuilder extends Component {
    componentDidMount() {
        this.props.expressionEditorCreated(this.props.expressionId);
    }

    componentWillUnmount() {
        this.props.expressionEditorDestroyed(this.props.expressionId);
    }

    render() {
        let { expressionId, 
            dataSource, 
            expression, 
            editor,
            isEditableSystemSet, 
            expressionSetEditable } = this.props;
                                
        let { isEditableUserSet } = editor;

        let roOperator = (
            <ROExpressionOperator 
                expressionId={expressionId}
                isEnabled={true}
                operator={expression}
                />
        )

        let editOperator = (
            <ExpressionOperator 
                        dataSource={dataSource}
                        expressionId={expressionId}
                        isRoot={true}
                        isEnabled={true}
                        operator={expression}  />
        )

        let theComponent;
        if (isEditableSystemSet) {
            theComponent = (
                <div>
                    <Checkbox 
                        label='Edit Mode'
                        toggle
                        checked={isEditableUserSet} 
                        onChange={() => expressionSetEditable(expressionId, !isEditableUserSet)} 
                        />
                    {isEditableUserSet ? editOperator : roOperator}
                </div>
            )
        } else {
            theComponent = roOperator;
        }

        return (
            <LineContainer 
                lineContextId={'expression-lines-' + expressionId}
                lineElementCreators={lineElementCreators}
                >
                {theComponent}
            </LineContainer>
        );
    }
}

ExpressionBuilder.propTypes = {
    dataSource : PropTypes.object.isRequired,
    expressionId : PropTypes.string.isRequired,
    expression: PropTypes.object.isRequired,
    editor : PropTypes.object.isRequired,
    isEditableSystemSet : PropTypes.bool.isRequired
}

ExpressionBuilder.defaultProps = {
    isEditableSystemSet : false
}

export default connect(
    (state) => ({
        // state
    }),
    {
        // actions
        expressionSetEditable,
        expressionEditorCreated,
        expressionEditorDestroyed
    }
)(withDataSource()(withExpression()(ExpressionBuilder)));

