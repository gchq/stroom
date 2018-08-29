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

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import { Input, Button, Icon, Dropdown, Confirm } from 'semantic-ui-react';

import { DragSource } from 'react-dnd';

import ItemTypes from './dragDropTypes';
import { displayValues } from './conditions';
import DocPicker from 'components/DocPicker';
import { actionCreators, joinDictionaryTermId } from './redux';

const { expressionItemUpdated, expressionItemDeleted } = actionCreators;

const withPendingDeletion = withState('pendingDeletion', 'setPendingDeletion', false);

const dragSource = {
  beginDrag(props) {
    return {
      ...props.term,
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
}

const enhance = compose(
  connect(
    state => ({
      // state
    }),
    {
      expressionItemUpdated,
      expressionItemDeleted,
    },
  ),
  DragSource(ItemTypes.TERM, dragSource, dragCollect),
  withPendingDeletion,
);

const ExpressionTerm = ({
  connectDragSource,
  isDragging,
  term,
  isEnabled,
  dataSource,
  expressionId,

  pendingDeletion,
  setPendingDeletion,

  expressionItemDeleted,
  expressionItemUpdated,
}) => {
  const pickerId = joinDictionaryTermId(expressionId, term.uuid);

  const onTermUpdated = (updates) => {
    expressionItemUpdated(expressionId, term.uuid, updates);
  };

  const onDeleteTerm = () => {
    expressionItemDeleted(expressionId, term.uuid);
    setPendingDeletion(false);
  };

  const onCancelDeleteTerm = () => {
    setPendingDeletion(false);
  };

  const onRequestDeleteTerm = () => {
    setPendingDeletion(true);
  };

  const onEnabledChange = () => {
    onTermUpdated({
      enabled: !term.enabled,
    });
  };

  const onFieldChange = (event, data) => {
    onTermUpdated({
      field: data.value,
    });
  };

  const onConditionChange = (event, data) => {
    onTermUpdated({
      condition: data.value,
    });
  };

  const onFromValueChange = (event, data) => {
    const parts = term.value.split(',');
    const existingToValue = parts.length === 2 ? parts[1] : undefined;
    const newValue = `${data.value},${existingToValue}`;

    onTermUpdated({
      value: newValue,
    });
  };

  const onToValueChange = (event, data) => {
    const parts = term.value.split(',');
    const existingFromValue = parts.length === 2 ? parts[0] : undefined;
    const newValue = `${existingFromValue},${data.value}`;

    onTermUpdated({
      value: newValue,
    });
  };

  const onSingleValueChange = (event, data) => {
    onTermUpdated({
      value: data.value,
    });
  };

  const onDictionaryValueChange = (docRef) => {
    onTermUpdated({
      value: docRef,
    });
  };

  const onMultipleValueChange = (event, data) => {
    onTermUpdated({
      value: data.value.join(),
    });
  };

  let className = 'expression-item';
  if (!isEnabled) {
    className += ' expression-item--disabled';
  }

  let enabledButton;
  if (term.enabled) {
    enabledButton = <Button icon="checkmark" compact color="blue" onClick={onEnabledChange} />;
  } else {
    enabledButton = <Button icon="checkmark" compact color="grey" onClick={onEnabledChange} />;
  }

  const fieldOptions = dataSource.fields.map(f => ({
    value: f.name,
    text: f.name,
  }));

  const field = dataSource.fields.find(f => f.name === term.field);
  let conditionOptions = [];
  let valueType = 'text';
  if (field) {
    conditionOptions = field.conditions.map(c => ({
      value: c,
      text: displayValues[c],
    }));

    switch (field.type) {
      case 'FIELD':
      case 'ID':
        valueType = 'text';
        break;
      case 'NUMERIC_FIELD':
        valueType = 'number';
        break;
      case 'DATE_FIELD':
        valueType = 'datetime-local';
        break;
      default:
        throw new Error(`Invalid field type: ${field.type}`);
    }
  }

  let valueWidget;
  switch (term.condition) {
    case 'CONTAINS':
    case 'EQUALS':
    case 'GREATER_THAN':
    case 'GREATER_THAN_OR_EQUAL_TO':
    case 'LESS_THAN':
    case 'LESS_THAN_OR_EQUAL_TO': {
      valueWidget = (
        <Input
          placeholder="value"
          type={valueType}
          value={term.value || ''}
          onChange={onSingleValueChange}
        />
      ); // some single selection
      break;
    }
    case 'BETWEEN': {
      const splitValues = term.value.split(',');
      const fromValue = splitValues.length === 2 ? splitValues[0] : undefined;
      const toValue = splitValues.length === 2 ? splitValues[1] : undefined;
      valueWidget = (
        <span>
          <Input
            placeholder="from"
            type={valueType}
            value={fromValue}
            onChange={onFromValueChange}
          />
          <span className="input-between__divider">to</span>
          <Input placeholder="to" type={valueType} value={toValue} onChange={onToValueChange} />
        </span>
      ); // some between selection
      break;
    }
    case 'IN': {
      const hasValues = !!term.value && term.value.length > 0;
      const splitValues = hasValues ? term.value.split(',') : [];
      const keyedValues = hasValues ? splitValues.map(s => ({ key: s, value: s, text: s })) : [];
      valueWidget = (
        <Dropdown
          options={keyedValues}
          multiple
          value={splitValues}
          placeholder="type multiple values"
          search={(options, query) => [{ key: query, value: query, text: query }]}
          selection
          onChange={onMultipleValueChange}
        />
      );
      break;
    }
    case 'IN_DICTIONARY': {
      valueWidget = (
        <DocPicker
          pickerId={pickerId}
          typeFilters={['Dictionary']}
          onChange={onDictionaryValueChange}
          value={term.value}
        />
      );
      break;
    }
    default:
      throw new Error(`Invalid condition: ${term.condition}`);
  }

  return connectDragSource(<div className={className}>
    <span>
      <Icon name="bars" />
    </span>
    <Confirm
      open={!!pendingDeletion}
      content="This will delete the term, are you sure?"
      onCancel={onCancelDeleteTerm}
      onConfirm={onDeleteTerm}
    />
    <Dropdown
      placeholder="field"
      selection
      options={fieldOptions}
      onChange={onFieldChange}
      value={term.field}
    />
    <Dropdown
      placeholder="condition"
      selection
      options={conditionOptions}
      onChange={onConditionChange}
      value={term.condition}
    />
    {valueWidget}
    <Button.Group floated="right">
      {enabledButton}
      <Button compact icon="trash" onClick={onRequestDeleteTerm} />
    </Button.Group>
                           </div>);
};

ExpressionTerm.propTypes = {
  dataSource: PropTypes.object.isRequired, // complete definition of the data source
  expressionId: PropTypes.string.isRequired, // the ID of the overall expression
  term: PropTypes.object.isRequired, // the operator that this particular element is to represent
  isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own
};

export default enhance(ExpressionTerm);
