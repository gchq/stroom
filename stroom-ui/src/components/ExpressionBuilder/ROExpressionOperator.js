import React, { Component } from 'react';
import PropTypes from 'prop-types';

import ROExpressionTerm from './ROExpressionTerm';

import {
    Icon
} from 'semantic-ui-react';

import {
    LineTo
} from 'components/LineTo'

/**
 * Read only expression operator
 */
class ROExpressionOperator extends Component {
    static propTypes = {
        expressionId : PropTypes.string.isRequired, // the ID of the overall expression
        operator : PropTypes.object.isRequired, // the operator that this particular element is to represent
        isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own
    }

    renderChildren() {
        return this.props.operator.children.map(c => {
            let itemElement;
            let isEnabled = this.props.isEnabled && c.enabled;
            switch (c.type) {
                case 'term':
                    itemElement = (
                        <div key={c.uuid} id={'expression-item' + c.uuid}>
                            <ROExpressionTerm 
                                        expressionId={this.props.expressionId}
                                        isEnabled={isEnabled}
                                        term={c} />
                        </div>
                    )
                    break;
                case 'operator':
                    itemElement = (
                        <ROExpressionOperator 
                                    expressionId={this.props.expressionId}
                                    isEnabled={isEnabled}
                                    operator={c} />
                    )
                    break;
            }

            // Wrap it with a line to
            return (
                <div key={c.uuid}>
                    <LineTo 
                        lineId={c.uuid}
                        lineType='downRightElbow'
                        fromId={'expression-item' + this.props.operator.uuid} 
                        toId={'expression-item' + c.uuid}
                        />
                    {itemElement}
                </div>
            )
        }).filter(c => !!c) // null filter
    }

    render() {
        let {
            expressionId,
            operator,
            isRoot,
            isEnabled
        } = this.props;

        let className = 'expression-item';
        if (isRoot) {
            className += ' expression-item__root'
        }
        if (!isEnabled) {
            className += ' expression-item--disabled';
        }

        return (
            <div className={className}>
                <div>
                    <span id={'expression-item' + operator.uuid}><Icon name='circle'/></span>
                    <span>{operator.op}</span>
                </div>
                <div className='operator__children'>
                    {this.renderChildren()}
                </div>
            </div>
        )
    }
}

export default ROExpressionOperator;