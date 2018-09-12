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
import React from 'react';
import PropTypes from 'prop-types';
import { compose, withHandlers, withProps } from 'recompose';
import { connect } from 'react-redux';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { Button } from 'semantic-ui-react';
import { DragSource, DropTarget } from 'react-dnd';

import { canMove } from 'lib/treeUtils';
import ItemTypes from './dragDropTypes';
import ExpressionTerm from './ExpressionTerm';
import { actionCreators } from './redux';
import { LineTo } from 'components/LineTo';
import { LOGICAL_OPERATORS } from './logicalOperators';
import IconButton from 'components/IconButton';

const {
  expressionTermAdded,
  expressionOperatorAdded,
  expressionItemUpdated,
  expressionItemMoved,
  expressionItemDeleteRequested,
} = actionCreators;

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      ...props.operator,
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
}

const dropTarget = {
  canDrop(props, monitor) {
    return canMove(monitor.getItem(), props.operator);
  },
  drop(props, monitor) {
    props.expressionItemMoved(props.expressionId, monitor.getItem(), props.operator);
  },
};

function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop(),
  };
}

const enhance = compose(
  connect(
    state => ({
      // operators are nested, so take all their props from parent
    }),
    {
      expressionTermAdded,
      expressionOperatorAdded,
      expressionItemUpdated,
      expressionItemMoved,
      expressionItemDeleteRequested,
    },
  ),
  DragSource(ItemTypes.OPERATOR, dragSource, dragCollect),
  DropTarget([ItemTypes.OPERATOR, ItemTypes.TERM], dropTarget, dropCollect),
  withHandlers({
    onAddOperator: ({ expressionOperatorAdded, expressionId, operator: { uuid } }) => {
      expressionOperatorAdded(expressionId, uuid);
    },

    onAddTerm: ({ expressionTermAdded, expressionId, operator: { uuid } }) => () => {
      expressionTermAdded(expressionId, uuid);
    },

    onOperatorUpdated: ({ expressionItemUpdated, expressionId, operator: { uuid } }) => (updates) => {
      expressionItemUpdated(expressionId, uuid, updates);
    },

    onOpChange: ({ expressionItemUpdated, expressionId, operator: { uuid } }) => (op) => {
      expressionItemUpdated(expressionId, uuid, {
        op,
      });
    },

    onRequestDeleteOperator: ({
      expressionItemDeleteRequested,
      expressionId,
      operator: { uuid },
    }) => () => {
      expressionItemDeleteRequested(expressionId, uuid);
    },

    onEnabledToggled: ({
      isRoot,
      expressionItemUpdated,
      expressionId,
      operator: { uuid, enabled },
    }) => () => {
      if (!isRoot) {
        expressionItemUpdated(expressionId, uuid, {
          enabled: !enabled,
        });
      }
    },
  }),
  withProps(({
    canDrop, isOver, isRoot, isEnabled, operator,
  }) => {
    let dndBarColour = 'grey';
    if (isOver) {
      dndBarColour = canDrop ? 'blue' : 'red';
    }

    const classNames = ['expression-item'];
    if (isRoot) {
      classNames.push('expression-item__root');
    }
    if (!isEnabled) {
      classNames.push('expression-item--disabled');
    }

    let enabledIcon = 'check';
    let enabledColour = 'grey';
    if (isRoot) {
      enabledIcon = 'ban';
    } else if (operator.enabled) {
      enabledColour = 'blue';
    }

    return {
      enabledIcon,
      enabledColour,
      dndBarColour,
      className: classNames.join(' '),
    };
  }),
);

const ExpressionOperator = ({
  expressionId,
  operator,
  isRoot,
  isEnabled,
  dataSource,

  connectDropTarget,
  isOver,
  connectDragSource,
  isDragging,

  dndBarColour,
  className,

  onAddOperator,
  onAddTerm,
  onOperatorUpdated,
  onOpChange,
  onRequestDeleteOperator,
  onEnabledToggled,

  enabledIcon,
  enabledColour,
}) => (
    <div className={className}>
      {connectDropTarget(<div>
        {connectDragSource(<span id={`expression-item${operator.uuid}`}>
          <FontAwesomeIcon color={dndBarColour} icon="bars" />
        </span>)}

        <Button.Group>
          {LOGICAL_OPERATORS.map(l => (
            <Button
              color={operator.op === l ? 'blue' : undefined}
              key={l}
              compact
              onClick={() => onOpChange(l)}
            >
              {l}
            </Button>
          ))}
        </Button.Group>

        <IconButton icon="plus" text="Term" groupPosition='left' onClick={onAddTerm} />
        <IconButton icon="plus" text="Group" groupPosition='middle' onClick={onAddOperator} />
        <IconButton icon={enabledIcon} groupPosition='middle' color={enabledColour} onClick={onEnabledToggled} />
        {!isRoot ?
          (<IconButton icon="trash" groupPosition='right' onClick={onRequestDeleteOperator} />)
          :
          (<IconButton disabled icon="ban" groupPosition='right' />)
        }

      </div>)}

      <div className="operator__children">
        {isOver && dropTarget.canDrop && <div className="operator__placeholder" />}
        {operator.children
          .map((c) => {
            let itemElement;
            switch (c.type) {
              case 'term':
                itemElement = (
                  <div key={c.uuid} id={`expression-item${c.uuid}`}>
                    <ExpressionTerm
                      dataSource={dataSource}
                      expressionId={expressionId}
                      isEnabled={isEnabled && c.enabled}
                      term={c}
                    />
                  </div>
                );
                break;
              case 'operator':
                itemElement = (
                  <EnhancedExpressionOperator
                    dataSource={dataSource}
                    expressionId={expressionId}
                    isEnabled={isEnabled && c.enabled}
                    operator={c}
                  />
                );
                break;
              default:
                throw new Error(`Invalid operator type: ${c.type}`);
            }

            // Wrap it with a line to
            return (
              <div key={c.uuid}>
                <LineTo
                  lineId={c.uuid}
                  lineType="downRightElbow"
                  fromId={`expression-item${operator.uuid}`}
                  toId={`expression-item${c.uuid}`}
                />
                {itemElement}
              </div>
            );
          })
          .filter(c => !!c) // null filter
        }
      </div>
    </div>
  );

const EnhancedExpressionOperator = enhance(ExpressionOperator);

EnhancedExpressionOperator.propTypes = {
  dataSource: PropTypes.object.isRequired, // complete definition of the data source
  expressionId: PropTypes.string.isRequired, // the ID of the overall expression
  operator: PropTypes.object.isRequired, // the operator that this particular element is to represent
  isRoot: PropTypes.bool.isRequired, // used to prevent deletion of root nodes
  isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own
};

EnhancedExpressionOperator.defaultProps = {
  isRoot: false,
};

export default EnhancedExpressionOperator;
