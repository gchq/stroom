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
import PropTypes from 'prop-types'

import { connect } from 'react-redux'

import { 
    Icon, 
    Button, 
    ButtonGroup
} from 'semantic-ui-react'

import { DragSource, DropTarget } from 'react-dnd';

import { canMove } from 'lib/treeUtils';
import { ItemTypes } from './dragDropTypes';

import ExpressionTerm from './ExpressionTerm';

import {
    expressionTermAdded,
    expressionOperatorAdded,
    expressionItemUpdated,
    expressionItemMoved,
    expressionItemDeleted
} from './redux';

import {
    LineTo
} from 'components/LineTo'

const LOGICAL_OPERATORS = [
    "NOT", 
    "AND",
    "OR"
]

const dragSource = {
	canDrag(props) {
		return true;
	},
    beginDrag(props) {
        return {
            ...props.operator
        };
    }
};

function dragCollect(connect, monitor) {
    return {
        connectDragSource: connect.dragSource(),
        isDragging: monitor.isDragging()
    }
}

const dropTarget = {
    canDrop(props, monitor) {
        return canMove(monitor.getItem(), props.operator)
    },
    drop(props, monitor) {
        props.expressionItemMoved(props.expressionId, monitor.getItem(), props.operator);
    }
}

function dropCollect(connect, monitor) {
    return {
      connectDropTarget: connect.dropTarget(),
      isOver: monitor.isOver(),
      canDrop: monitor.canDrop()
    };
}

class ExpressionOperator extends Component {
    
    static propTypes = {
        // Props
        dataSource: PropTypes.object.isRequired, // complete definition of the data source
        expressionId : PropTypes.string.isRequired, // the ID of the overall expression
        operator : PropTypes.object.isRequired, // the operator that this particular element is to represent
        isRoot : PropTypes.bool.isRequired, // used to prevent deletion of root nodes
        isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own

        // Actions
        expressionTermAdded : PropTypes.func.isRequired,
        expressionOperatorAdded : PropTypes.func.isRequired,
        expressionItemUpdated : PropTypes.func.isRequired,
        expressionItemDeleted : PropTypes.func.isRequired,
        expressionItemMoved : PropTypes.func.isRequired,
        
        // React DnD
        connectDropTarget: PropTypes.func.isRequired,
        isOver: PropTypes.bool.isRequired,
        connectDragSource: PropTypes.func.isRequired,
        isDragging: PropTypes.bool.isRequired
    }
    
    static defaultProps = {
        isRoot : false
    }

    onAddOperator() {
        this.props.expressionOperatorAdded(this.props.expressionId, this.props.operator.uuid);
    }

    onAddTerm() {
        this.props.expressionTermAdded(this.props.expressionId, this.props.operator.uuid);
    }

    onOperatorUpdated(updates) {
        this.props.expressionItemUpdated(this.props.expressionId, this.props.operator.uuid, updates);
    }

    onOpChange(op) {
        this.onOperatorUpdated({
            op
        });
    }

    onOperatorDelete() {
        this.props.expressionItemDeleted(this.props.expressionId, this.props.operator.uuid);
    }

    onEnabledChange() {
        this.onOperatorUpdated({
            enabled: !this.props.operator.enabled
        });
    }

    renderChildren() {
        return this.props.operator.children.map(c => {
            let itemElement;
            switch (c.type) {
                case 'term':
                    itemElement = (
                        <div key={c.uuid} id={'expression-item' + c.uuid}>
                            <ExpressionTerm 
                                        dataSource={this.props.dataSource} 
                                        expressionId={this.props.expressionId}
                                        isEnabled={this.props.isEnabled && c.enabled}
                                        term={c} />
                        </div>
                    )
                    break;
                case 'operator':
                    itemElement = (
                        <DndExpressionOperator 
                                    dataSource={this.props.dataSource}  
                                    expressionId={this.props.expressionId}
                                    isEnabled={this.props.isEnabled && c.enabled}
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
        const { connectDragSource, isDragging, connectDropTarget, isOver, canDrop } = this.props;

        let color = 'grey';
        if (isOver) {
            color= (canDrop) ? 'blue' : 'red'
        }
        let className = 'expression-item';
        if (this.props.isRoot) {
            className += ' expression-item__root'
        }
        if (!this.props.isEnabled) {
            className += ' expression-item--disabled';
        }

        let enabledButton;
        if (this.props.isRoot) {
            enabledButton = <Button 
                                icon='dont'
                                compact
                                basic
                                />
        } else {
            if (this.props.operator.enabled) {
                enabledButton = <Button icon='checkmark'
                                    compact 
                                    color='blue'
                                    onClick={this.onEnabledChange.bind(this)}
                                    /> 
            } else {
                enabledButton = <Button icon='checkmark'
                                    compact
                                    basic
                                    onClick={this.onEnabledChange.bind(this)}
                                    />
            }
        }

        return (
            <div className={className}>
                {connectDragSource(connectDropTarget(
                    <div>
                        <span id={'expression-item' + this.props.operator.uuid}><Icon color={color} name='bars'/></span>
                        
                        <Button.Group>
                            {LOGICAL_OPERATORS.map(l => {
                                return (
                                    <Button 
                                        color='blue'
                                        basic={(this.props.operator.op !== l)}
                                        key={l}
                                        compact
                                        onClick={() => this.onOpChange(l)}
                                        >
                                        {l}
                                    </Button>
                                )
                            })}
                        </Button.Group>

                        <Button.Group floated='right'>
                            <Button compact onClick ={this.onAddTerm.bind(this)}>
                                <Icon name='add' />
                                Term
                            </Button>
                            <Button compact onClick={this.onAddOperator.bind(this)}>
                                <Icon name='add' />
                                Group
                            </Button>
                            {enabledButton}
                            {!this.props.isRoot ? 
                                <Button icon='trash' compact onClick={this.onOperatorDelete.bind(this)} />
                                :
                                <Button disabled icon='dont' compact />
                            }
                        </Button.Group>
                    </div>
                ))}
                <div className='operator__children'>
                    {(isOver && canDrop) && <div className='operator__placeholder' />}
                    {this.renderChildren()}
                </div>
            </div>
        )
    }
}

// We need to use this ourself, so create a variable
const DndExpressionOperator = connect(
    (state) => ({
        // operators are nested, so take all their props from parent
    }),
    {
        expressionTermAdded,
        expressionOperatorAdded,
        expressionItemUpdated,
        expressionItemMoved,
        expressionItemDeleted,
    }
)
    (DragSource(ItemTypes.OPERATOR, dragSource, dragCollect)(
        DropTarget([ItemTypes.OPERATOR, ItemTypes.TERM], dropTarget, dropCollect)(
            ExpressionOperator
        )
    ));

export default DndExpressionOperator;