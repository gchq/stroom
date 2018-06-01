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

import { compose } from 'redux';
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
    requestExpressionItemDelete
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

const ExpressionOperator = ({
    expressionId,
    operator,
    isRoot,
    isEnabled,
    dataSource,

    expressionTermAdded,
    expressionOperatorAdded,
    expressionItemUpdated,
    requestExpressionItemDelete,
    expressionItemMoved,

    connectDropTarget,
    isOver,
    connectDragSource,
    isDragging
}) => {

    let onAddOperator = () => {
        expressionOperatorAdded(expressionId, operator.uuid);
    }

    let onAddTerm = () => {
        expressionTermAdded(expressionId, operator.uuid);
    }

    let onOperatorUpdated = (updates) => {
        expressionItemUpdated(expressionId, operator.uuid, updates);
    }

    let onOpChange = (op) => {
        onOperatorUpdated({
            op
        });
    }

    let onOperatorDelete = () => {
        requestExpressionItemDelete(expressionId, operator.uuid);
    }

    let onEnabledChange = () => {
        onOperatorUpdated({
            enabled: !operator.enabled
        });
    }

    let color = 'grey';
    if (isOver) {
        color= (dropTarget.canDrop) ? 'blue' : 'red'
    }
    let className = 'expression-item';
    if (isRoot) {
        className += ' expression-item__root'
    }
    if (!isEnabled) {
        className += ' expression-item--disabled';
    }

    let enabledButton;
    if (isRoot) {
        enabledButton = <Button 
                            icon='dont'
                            compact
                            basic
                            />
    } else {
        if (operator.enabled) {
            enabledButton = <Button icon='checkmark'
                                compact 
                                color='blue'
                                onClick={onEnabledChange}
                                /> 
        } else {
            enabledButton = <Button icon='checkmark'
                                compact
                                basic
                                onClick={onEnabledChange}
                                />
        }
    }

    return (
        <div className={className}>
            {connectDragSource(connectDropTarget(
                <div>
                    <span id={'expression-item' + operator.uuid}><Icon color={color} name='bars'/></span>
                    
                    <Button.Group>
                        {LOGICAL_OPERATORS.map(l => {
                            return (
                                <Button 
                                    color='blue'
                                    basic={(operator.op !== l)}
                                    key={l}
                                    compact
                                    onClick={() => onOpChange(l)}
                                    >
                                    {l}
                                </Button>
                            )
                        })}
                    </Button.Group>

                    <Button.Group floated='right'>
                        <Button compact onClick ={onAddTerm}>
                            <Icon name='add' />
                            Term
                        </Button>
                        <Button compact onClick={onAddOperator}>
                            <Icon name='add' />
                            Group
                        </Button>
                        {enabledButton}
                        {!isRoot ? 
                            <Button icon='trash' compact onClick={onOperatorDelete} />
                            :
                            <Button disabled icon='dont' compact />
                        }
                    </Button.Group>
                </div>
            ))}
            <div className='operator__children'>
                {(isOver && dropTarget.canDrop) && <div className='operator__placeholder' />}
                {
                    operator.children.map(c => {
                        let itemElement;
                        switch (c.type) {
                            case 'term':
                                itemElement = (
                                    <div key={c.uuid} id={'expression-item' + c.uuid}>
                                        <ExpressionTerm 
                                                    dataSource={dataSource} 
                                                    expressionId={expressionId}
                                                    isEnabled={isEnabled && c.enabled}
                                                    term={c} />
                                    </div>
                                )
                                break;
                            case 'operator':
                                itemElement = (
                                    <DndExpressionOperator 
                                                dataSource={dataSource}  
                                                expressionId={expressionId}
                                                isEnabled={isEnabled && c.enabled}
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
                                    fromId={'expression-item' + operator.uuid} 
                                    toId={'expression-item' + c.uuid}
                                    />
                                {itemElement}
                            </div>
                        )
                    }).filter(c => !!c) // null filter
                }
            </div>
        </div>
    )
}

ExpressionOperator.propTypes = {
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
    requestExpressionItemDelete : PropTypes.func.isRequired,
    expressionItemMoved : PropTypes.func.isRequired,
    
    // React DnD
    connectDropTarget: PropTypes.func.isRequired,
    isOver: PropTypes.bool.isRequired,
    connectDragSource: PropTypes.func.isRequired,
    isDragging: PropTypes.bool.isRequired
}

ExpressionOperator.defaultProps = {
    isRoot : false
}

// We need to use this ourself, so create a variable
const DndExpressionOperator = compose(
    connect(
        (state) => ({
            // operators are nested, so take all their props from parent
        }),
        {
            expressionTermAdded,
            expressionOperatorAdded,
            expressionItemUpdated,
            expressionItemMoved,
            requestExpressionItemDelete,
        }
    ),
    DragSource(ItemTypes.OPERATOR, dragSource, dragCollect),
    DropTarget([ItemTypes.OPERATOR, ItemTypes.TERM], dropTarget, dropCollect)
)(ExpressionOperator);

export default DndExpressionOperator;