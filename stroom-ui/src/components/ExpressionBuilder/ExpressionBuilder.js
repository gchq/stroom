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

import ExpressionOperator from './ExpressionOperator';
import { LineContainer } from 'prototypes/LineTo'

import './ExpressionBuilder.css'

const defaultExpression = {
    "uuid" : "root",
    "type" : "operator",
    "op" : "AND",
    "children": [],
    "enabled" : true
};

class ExpressionBuilder extends Component {
    static propTypes = {
        dataSourceUuid: PropTypes.string.isRequired,
        expressionId: PropTypes.string.isRequired,
        expressions: PropTypes.object.isRequired, // expects the entire map of expressions to be available
    }

    state = {
        expression : undefined,
        dataSource : undefined
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        return {
            expression : nextProps.expressions[nextProps.expressionId],
            dataSource : nextProps.dataSources[nextProps.dataSourceUuid]
        }
    }

    render() {
        if (!!this.state.expression && !!this.state.dataSource) {
            return (
                <LineContainer id={'expression-lines-' + this.props.expressionId}>
                    <ExpressionOperator 
                        dataSource={this.state.dataSource}
                        expressionId={this.props.expressionId}
                        isRoot={true}
                        isEnabled={true}
                        operator={this.state.expression}  />
                </LineContainer>
            )
        } else {
            return <div>Error - Data Source ({!this.state.dataSource ? 'missing' : 'ok'}), Expression ({!this.state.expression ? 'missing' : 'ok'})</div>
        }
    }
}

export default connect(
    (state) => ({
        dataSources : state.dataSources,
        expressions : state.expressions
    }),
    {
        // actions
    }
)(ExpressionBuilder);

