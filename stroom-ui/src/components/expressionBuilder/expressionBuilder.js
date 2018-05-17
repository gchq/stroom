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

import ExpressionOperator from './expressionOperator';

import './expressionBuilder.css'

const defaultExpression = {
    "uuid" : "root",
    "type" : "operator",
    "op" : "AND",
    "children": [],
    "enabled" : true
};

const ExpressionBuilder = ({dataSourceUuid, expressionId, dataSources, expressions, expressionChanged}) => {
    let expression = expressions[expressionId];
    let dataSource = dataSources[dataSourceUuid];

    if (!!expression && !!dataSource) {
        return (
            <ExpressionOperator 
                dataSource={dataSource}
                expressionId={expressionId}
                isRoot={true}
                isEnabled={true}
                operator={expression}  />
        )
    } else {
        return <div>Error - Data Source ({!dataSource ? 'missing' : 'ok'}), Expression ({!expression ? 'missing' : 'ok'})</div>
    }
}

ExpressionBuilder.propTypes = {
    dataSourceUuid: PropTypes.string.isRequired,
    expressionId: PropTypes.string.isRequired,
    expressions: PropTypes.object.isRequired, // expects the entire map of expressions to be available
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

