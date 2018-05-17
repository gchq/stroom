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

