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
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "components/Button";
import ExpressionTerm from "components/ExpressionBuilder/ExpressionTerm/ExpressionTerm";
import { LineEndpoint, LineTo } from "components/LineTo";
import { canMove } from "lib/treeUtils/treeUtils";
import { pipe } from "ramda";
import * as React from "react";
import {
  DragSource,
  DragSourceCollector,
  DragSourceSpec,
  DropTarget,
  DropTargetCollector,
  DropTargetSpec,
} from "react-dnd";
import { getNewOperator, getNewTerm } from "../expressionUtils";
import {
  DataSourceType,
  DragCollectedProps,
  DragDropTypes,
  DragObject,
  DropCollectedProps,
  ExpressionItem,
  ExpressionOperatorType,
  ExpressionTermType,
  OperatorType,
  OperatorTypeValues,
} from "../types";
import InlineSelect from "components/InlineSelect/InlineSelect";
import { useCallback } from "react";

interface Props {
  index?: number; // If this is undefined, assume this is the root
  idWithinExpression?: string;
  dataSource: DataSourceType;
  isEnabled?: boolean;
  value: ExpressionOperatorType;
  onChange: (e: ExpressionOperatorType, i: number | undefined) => void;
  onDelete?: (i: number) => void;
}

interface EnhancedProps extends Props, DragCollectedProps, DropCollectedProps {}

const dragSource: DragSourceSpec<Props, DragObject> = {
  canDrag() {
    return true;
  },
  beginDrag(props) {
    return {
      expressionItem: props.value,
    };
  },
};

const dropTarget: DropTargetSpec<Props> = {
  canDrop(props, monitor) {
    return canMove(monitor.getItem(), props.value);
  },
  drop(props, monitor) {
    //TODO fix drag and drop
    console.log("Drag and Drop", {
      item: monitor.getItem().expressionItem,
      operator: props.value,
    });
    //props.expressionItemMoved(monitor.getItem().expressionItem, props.operator);
  },
};

const dropCollect: DropTargetCollector<
  DropCollectedProps,
  Props
> = function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop(),
  };
};

export const dragCollect: DragSourceCollector<
  DragCollectedProps,
  Props
> = function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
};

// dnd_error: temporarily disable dnd-related code to get the build working
/* const enhance = pipe(
 *   DragSource(DragDropTypes.OPERATOR, dragSource, dragCollect),
 *   DropTarget(
 *     [DragDropTypes.OPERATOR, DragDropTypes.TERM],
 *     dropTarget,
 *     dropCollect,
 *   ),
 * ); */

const ExpressionOperator: React.FunctionComponent<EnhancedProps> = ({
  value,
  index,
  idWithinExpression = "root",
  isEnabled = true,
  dataSource,
  onChange,
  onDelete,

  connectDropTarget,
  isOver,
  canDrop,
  connectDragSource,
}) => {
  const isRoot = index === undefined;

  const onDeleteThis = useCallback(() => {
    if (!!index && onDelete) {
      onDelete(index);
    }
  }, [index, onDelete]);

  const onAddOperator = useCallback(() => {
    onChange(
      {
        ...value,
        children: [...value.children, getNewOperator()],
      },
      index,
    );
  }, [index, value, onChange]);

  const onAddTerm = useCallback(() => {
    onChange(
      {
        ...value,
        children: [...value.children, getNewTerm()],
      },
      index,
    );
  }, [index, value, onChange]);

  const onOpChange = useCallback(
    (op: OperatorType) => {
      onChange(
        {
          ...value,
          op,
        },
        index,
      );
    },
    [value, index, onChange],
  );

  const onEnabledToggled = useCallback(() => {
    if (!!index) {
      onChange(
        {
          ...value,
          enabled: !value.enabled,
        },
        index,
      );
    }
  }, [index, value, onChange]);

  const onChildUpdated = useCallback(
    (_value: ExpressionTermType | ExpressionOperatorType, _index: number) => {
      onChange(
        {
          ...value,
          children: value.children.map((c, i) => (i === _index ? _value : c)),
        },
        index,
      );
    },
    [index, value, onChange],
  );

  const onChildDeleted = useCallback(
    (_index: number) => {
      onChange(
        {
          ...value,
          children: value.children.filter((c, i) => i !== _index),
        },
        index,
      );
    },
    [index, value, onChange],
  );

  let dndBarColour = "grey";
  if (isOver) {
    dndBarColour = canDrop ? "blue" : "red";
  }

  const classNames = ["expression-item"];
  if (isRoot) {
    classNames.push("expression-item__root");
  }
  if (!isEnabled) {
    classNames.push("expression-item--disabled");
  }

  // let enabledColour = "grey";
  // if (value.enabled) {
  //   enabledColour = "blue";
  // }

  const className = classNames.join(" ");

  const operatorTypeValues = OperatorTypeValues.map((o) => {
    return { value: o, label: o };
  });

  return (
    <div className={className}>
      {connectDropTarget(
        <div className={"expression-item__row"}>
          {connectDragSource(
            <div className="ExpressionOperator__circle">
              <LineEndpoint
                className="ExpressionOperator__circle"
                lineEndpointId={idWithinExpression}
              >
                <FontAwesomeIcon
                  className="ExpressionOperator__gripper"
                  color={dndBarColour}
                  icon="grip-vertical"
                />
              </LineEndpoint>
            </div>,
          )}

          <InlineSelect
            selected={value.op}
            onChange={(event) => onOpChange(event.target.value as OperatorType)}
            options={operatorTypeValues}
          />

          <div className="ExpressionItem__buttons">
            <Button size="small" icon="plus" onClick={onAddTerm}>
              Term
            </Button>
            <Button size="small" icon="plus" onClick={onAddOperator}>
              Group
            </Button>
            {!isRoot && (
              <React.Fragment>
                <Button
                  appearance="icon"
                  size="small"
                  icon="check"
                  disabled={!value.enabled}
                  onClick={onEnabledToggled}
                />
                <Button
                  appearance="icon"
                  size="small"
                  icon="trash"
                  onClick={onDeleteThis}
                />
              </React.Fragment>
            )}
          </div>
        </div>,
      )}

      <div className="operator__children">
        {isOver && dropTarget.canDrop && (
          <div className="operator__placeholder" />
        )}
        {value.children &&
          value.children.map((c: ExpressionItem, i: number) => {
            let itemElement;
            const itemLineEndpointId = `${idWithinExpression}-${i}`;
            switch (c.type) {
              case "term":
                itemElement = (
                  <ExpressionTerm
                    index={i}
                    idWithinExpression={itemLineEndpointId}
                    dataSource={dataSource}
                    isEnabled={isEnabled && c.enabled}
                    value={c as ExpressionTermType}
                    onDelete={onChildDeleted}
                    onChange={onChildUpdated}
                  />
                );
                break;
              case "operator":
                {
                  /* itemElement = (
                        <EnhancedExpressionOperator
                        index={i}
                        idWithinExpression={itemLineEndpointId}
                        dataSource={dataSource}
                        isEnabled={isEnabled && c.enabled}
                        value={c as ExpressionOperatorType}
                        onDelete={onChildDeleted}
                        onChange={onChildUpdated}
                        />
                        ); */
                }
                break;
              default:
                throw new Error(`Invalid operator type: ${c.type}`);
            }

            // Wrap it with a line to
            return (
              <div key={i} className="operator__child">
                <LineTo fromId={idWithinExpression} toId={itemLineEndpointId} />
                {itemElement}
              </div>
            );
          })}
      </div>
    </div>
  );
};

/* const EnhancedExpressionOperator = enhance(ExpressionOperator); */
const EnhancedExpressionOperator = ExpressionOperator;

export default EnhancedExpressionOperator;
