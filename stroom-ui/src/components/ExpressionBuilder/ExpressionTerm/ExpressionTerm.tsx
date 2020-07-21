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
import InlineSelect from "components/InlineSelect/InlineSelect";
import { LineEndpoint } from "components/LineTo";
import * as React from "react";
import { ChangeEvent, useCallback } from "react";
import { DragSource, DragSourceCollector, DragSourceSpec } from "react-dnd";
import Button from "../../Button";
import ConditionPicker from "../ConditionPicker/ConditionPicker";
import {
  ConditionType,
  DataSourceFieldType,
  DataSourceType,
  DragCollectedProps,
  DragDropTypes,
  DragObject,
  ExpressionTermType,
} from "../types";
import ValueWidget from "../ValueWidget";
import withValueType from "../withValueType";

interface Props {
  index: number;
  idWithinExpression: string;
  dataSource: DataSourceType;
  isEnabled: boolean;
  onDelete: (index: number) => void;
  value: ExpressionTermType;
  onChange: (e: ExpressionTermType, index: number) => void;
}

interface EnhancedProps extends Props, DragCollectedProps {}

const dragSource: DragSourceSpec<Props, DragObject> = {
  beginDrag(props) {
    return {
      expressionItem: props.value,
    };
  },
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

const enhance = DragSource(DragDropTypes.TERM, dragSource, dragCollect);

const ExpressionTerm: React.FunctionComponent<EnhancedProps> = ({
  onDelete,
  connectDragSource,
  value,
  isEnabled,
  onChange,
  dataSource,
  index,
  idWithinExpression,
}) => {
  const onDeleteThis = useCallback(() => {
    onDelete(index);
  }, [index, onDelete]);

  const onEnabledToggled = useCallback(() => {
    onChange(
      {
        ...value,
        enabled: !value.enabled,
      },
      index,
    );
  }, [index, value, onChange]);

  const onFieldChange = useCallback(
    (event: ChangeEvent<HTMLSelectElement>) => {
      const field = event.target.value;
      onChange(
        {
          ...value,
          field,
        },
        index,
      );
    },
    [index, value, onChange],
  );

  const onConditionChange = useCallback(
    (condition: ConditionType) => {
      onChange(
        {
          ...value,
          condition,
        },
        index,
      );
    },
    [index, value, onChange],
  );

  const onValueChange = useCallback(
    (v: string) => onChange({ ...value, value: v }, index),
    [index, value, onChange],
  );

  const classNames = ["expression-item", "expression-term"];

  if (!isEnabled) {
    classNames.push("expression-item--disabled");
  }

  const thisField = dataSource.fields.find(
    (f: DataSourceFieldType) => f.name === value.field,
  );

  let conditionOptions: ConditionType[] = [];
  if (thisField) {
    conditionOptions = thisField.conditions;
  }

  const className = classNames.join(" ");

  const valueType = withValueType(value, dataSource);

  return (
    <div className={className}>
      <div className={"ExpressionTerm__expression"}>
        {connectDragSource(
          <div className="ExpressionOperator__circle">
            <LineEndpoint
              lineEndpointId={idWithinExpression}
              className="ExpressionOperator__circle"
            >
              <FontAwesomeIcon
                className="ExpressionOperator__gripper"
                icon="grip-vertical"
              />
            </LineEndpoint>
          </div>,
        )}
        <div className="ExpressionOperator__expression">
          <InlineSelect
            options={dataSource.fields.map((field) => {
              return { value: field.name, label: field.name };
            })}
            selected={value.field}
            onChange={onFieldChange}
          />
          <ConditionPicker
            value={value.condition}
            onChange={onConditionChange}
            conditionOptions={conditionOptions}
          />
          <ValueWidget
            valueType={valueType}
            term={value}
            onChange={onValueChange}
          />
          <div className="expression-term__actions">
            <Button
              appearance="icon"
              size="small"
              icon="check"
              disabled={value.enabled}
              onClick={onEnabledToggled}
            />
            <Button
              icon="trash"
              size="small"
              appearance="icon"
              action="secondary"
              onClick={onDeleteThis}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default enhance(ExpressionTerm);
