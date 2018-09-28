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

import * as React from "react";
import { compose, withProps, withHandlers } from "recompose";
import { connect } from "react-redux";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { DragSource, DragSourceSpec } from "react-dnd";

import SelectBox from "../SelectBox";
import Button from "../Button";
import {
  DragDropTypes,
  DragObject,
  dragCollect,
  DragCollectedProps
} from "./dragDropTypes";
import { displayValues } from "./conditions";
import ValueWidget from "./ValueWidget";
import { actionCreators } from "./redux";
import withValueType, {
  WithProps as WithValueTypeProps
} from "./withValueType";
import {
  ExpressionTermType,
  DataSourceType,
  ConditionType,
  DataSourceFieldType,
  SelectOptionType
} from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const { expressionItemUpdated, expressionItemDeleteRequested } = actionCreators;

export interface Props {
  dataSource: DataSourceType;
  expressionId: string;
  term: ExpressionTermType;
  isEnabled: boolean;
}
export interface ConnectState {}
export interface ConnectDispatch {
  expressionItemUpdated: typeof expressionItemUpdated;
  expressionItemDeleteRequested: typeof expressionItemDeleteRequested;
}

export interface DndProps extends Props, ConnectState, ConnectDispatch {}

export interface WithHandlers {
  onRequestDeleteTerm: () => void;
  onEnabledToggled: () => void;
  onFieldChange: (field: string) => void;
  onConditionChange: (condition: ConditionType) => void;
  onValueChange: (value: any) => void;
}

export interface WithProps {
  conditionOptions: Array<SelectOptionType>;
  fieldOptions: Array<SelectOptionType>;
  className: string;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    DragCollectedProps,
    WithHandlers,
    WithProps,
    WithValueTypeProps {}

const dragSource: DragSourceSpec<DndProps, DragObject> = {
  beginDrag(props) {
    return {
      expressionItem: props.term
    };
  }
};

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    state => ({
      // state
    }),
    {
      expressionItemUpdated,
      expressionItemDeleteRequested
    }
  ),
  DragSource(DragDropTypes.TERM, dragSource, dragCollect),
  withHandlers<Props & ConnectDispatch & ConnectState, WithHandlers>({
    onRequestDeleteTerm: ({
      expressionItemDeleteRequested,
      expressionId,
      term: { uuid }
    }) => () => {
      expressionItemDeleteRequested(expressionId, uuid);
    },

    onEnabledToggled: ({
      expressionItemUpdated,
      expressionId,
      term: { uuid, enabled }
    }) => () => {
      expressionItemUpdated(expressionId, uuid, {
        enabled: !enabled
      });
    },

    onFieldChange: ({
      expressionItemUpdated,
      expressionId,
      term: { uuid }
    }) => value => {
      expressionItemUpdated(expressionId, uuid, {
        field: value
      });
    },

    onConditionChange: ({
      expressionItemUpdated,
      expressionId,
      term: { uuid }
    }) => value => {
      expressionItemUpdated(expressionId, uuid, {
        condition: value
      });
    },

    onValueChange: ({
      expressionItemUpdated,
      expressionId,
      term: { uuid }
    }) => value => expressionItemUpdated(expressionId, uuid, { value })
  }),
  withProps(({ isEnabled, term, dataSource }) => {
    const classNames = ["expression-item"];

    if (!isEnabled) {
      classNames.push("expression-item--disabled");
    }

    const fieldOptions = dataSource.fields.map((f: DataSourceFieldType) => ({
      value: f.name,
      text: f.name
    }));

    const thisField = dataSource.fields.find(
      (f: DataSourceFieldType) => f.name === term.field
    );

    let conditionOptions = [];
    if (thisField) {
      conditionOptions = thisField.conditions.map((c: ConditionType) => ({
        value: c,
        text: displayValues[c]
      }));
    }

    return {
      conditionOptions,
      fieldOptions,
      className: classNames.join(" ")
    };
  }),
  withValueType
);

const ExpressionTerm = ({
  connectDragSource,
  term,
  className,

  onRequestDeleteTerm,
  onEnabledToggled,
  onFieldChange,
  onConditionChange,
  onValueChange,

  fieldOptions,
  conditionOptions,
  valueType
}: EnhancedProps) => (
  <div className={`expression-term ${className}`}>
    {connectDragSource(
      <span>
        <FontAwesomeIcon icon="bars" />
      </span>
    )}
    <SelectBox
      placeholder="Field"
      value={term.field}
      onChange={onFieldChange}
      options={fieldOptions}
    />
    <SelectBox
      placeholder="Condition"
      value={term.condition}
      onChange={onConditionChange}
      options={conditionOptions}
    />

    <ValueWidget valueType={valueType} term={term} onChange={onValueChange} />
    <div className="expression-term__spacer" />
    <div className="expression-term__actions">
      <Button
        icon="check"
        groupPosition="left"
        disabled={term.enabled}
        onClick={onEnabledToggled}
      />
      <Button
        icon="trash"
        groupPosition="right"
        onClick={onRequestDeleteTerm}
      />
    </div>
  </div>
);

export default enhance(ExpressionTerm);
