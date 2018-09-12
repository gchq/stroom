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
import { compose, withProps, withHandlers } from 'recompose';
import { connect } from 'react-redux';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { DragSource } from 'react-dnd';

import SelectBox from 'components/SelectBox';
import Button from 'components/Button';
import ItemTypes from './dragDropTypes';
import { displayValues } from './conditions';
import ValueWidget from './ValueWidget';
import { actionCreators } from './redux';
import withValueType from './withValueType';

const { expressionItemUpdated, expressionItemDeleteRequested } = actionCreators;

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
      expressionItemDeleteRequested
    },
  ),
  DragSource(ItemTypes.TERM, dragSource, dragCollect),
  withHandlers({
    onRequestDeleteTerm: ({
      expressionItemDeleteRequested,
      expressionId,
      term: { uuid },
    }) => () => {
      expressionItemDeleteRequested(expressionId, uuid);
    },

    onEnabledToggled: ({ expressionItemUpdated, expressionId, term: { uuid, enabled } }) => () => {
      expressionItemUpdated(expressionId, uuid, {
        enabled: !enabled,
      });
    },

    onFieldChange: ({ expressionItemUpdated, expressionId, term: { uuid } }) => (value) => {
      expressionItemUpdated(expressionId, uuid, {
        field: value,
      });
    },

    onConditionChange: ({ expressionItemUpdated, expressionId, term: { uuid } }) => (value) => {
      expressionItemUpdated(expressionId, uuid, {
        condition: value,
      });
    },

    onValueChange: ({ expressionItemUpdated, expressionId, term: { uuid } }) => value =>
      expressionItemUpdated(expressionId, uuid, { value })
  }),
  withProps(({ isEnabled, term, dataSource }) => {
    const classNames = ['expression-item'];

    if (!isEnabled) {
      classNames.push('expression-item--disabled');
    }

    const fieldOptions = dataSource.fields.map(f => ({
      value: f.name,
      text: f.name,
    }));

    const thisField = dataSource.fields.find(f => f.name === term.field);

    let conditionOptions = [];
    if (thisField) {
      conditionOptions = thisField.conditions.map(c => ({
        value: c,
        text: displayValues[c],
      }));
    }

    return {
      conditionOptions,
      fieldOptions,
      className: classNames.join(' ')
    };
  }),
  withValueType
);

const ExpressionTerm = ({
  connectDragSource,
  term,
  enabledButtonColour,
  dataSource,
  expressionId,
  className,

  onRequestDeleteTerm,
  onEnabledToggled,
  onFieldChange,
  onConditionChange,
  onValueChange,

  fieldOptions,
  conditionOptions,
  valueType
}) => (
    <div className={`expression-term ${className}`}>
      {connectDragSource(<span>
        <FontAwesomeIcon icon="bars" />
      </span>)}
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

      <ValueWidget
        valueType={valueType}
        term={term}
        onChange={onValueChange}
      />
      <div className="expression-term__spacer" />
      <div className="expression-term__actions">
        <Button icon="check" groupPosition='left' disabled={term.enabled} onClick={onEnabledToggled} />
        <Button icon="trash" groupPosition='right' onClick={onRequestDeleteTerm} />
      </div>
    </div>
  );

ExpressionTerm.propTypes = {
  dataSource: PropTypes.object.isRequired, // complete definition of the data source
  expressionId: PropTypes.string.isRequired, // the ID of the overall expression
  term: PropTypes.object.isRequired, // the operator that this particular element is to represent
  isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own
};

export default enhance(ExpressionTerm);
