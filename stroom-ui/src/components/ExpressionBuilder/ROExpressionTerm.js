import React, { Component } from 'react';
import PropTypes from 'prop-types';

/**
 * Read only expression operator
 */
const ROExpressionTerm = (props) => {
    const {
        term,
        isEnabled,
        expressionId
    } = props;

    let className = 'expression-item expression-item--readonly';
    if (!isEnabled) {
        className += ' expression-item--disabled';
    }

    return (
        <div className={className}>
            {term.field} {term.condition} {term.value}
        </div>
    )
}

ROExpressionTerm.propTypes = {
    // Props
    expressionId : PropTypes.string.isRequired, // the ID of the overall expression
    term : PropTypes.object.isRequired, // the operator that this particular element is to represent
    isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own
}

export default ROExpressionTerm;