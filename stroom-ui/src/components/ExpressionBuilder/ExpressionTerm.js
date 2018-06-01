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
import PropTypes from 'prop-types';

import { compose } from 'redux';
import { connect } from 'react-redux';

import { Input, Button, Icon, Dropdown } from 'semantic-ui-react';

import { DragSource } from 'react-dnd';

import { ItemTypes } from './dragDropTypes';
import { displayValues } from './conditions';
import { DocRefModalPicker, docRefPicked } from '../DocExplorer';

import { expressionItemUpdated, requestExpressionItemDelete, joinDictionaryTermId } from './redux';

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

/**
 * Higher Order Component to cope with sync'ing the pickedDocRef state with the dictionary in our expression.
 *
 * Having this HOC allows the Expression Term component to be stateless functional
 * @param {Component} WrappedComponent
 */
const withPickedDocRef = () => (WrappedComponent) => {
  const WithPickedDocRef = class extends Component {
      static propTypes = {
        docRefPicked: PropTypes.func.isRequired,
        pickedDocRefs: PropTypes.object.isRequired, // picked dictionary
        expressionId: PropTypes.string.isRequired, // the ID of the overall expression
        term: PropTypes.object.isRequired, // the operator that this particular element is to represent
      };

      componentDidMount() {
        this.checkDictionary();
      }

      componentDidUpdate(prevProps, prevState, snapshot) {
        this.checkDictionary();
      }

      /**
       * This functions is used to check that the picked doc ref matches the one from the expression.
       * If it doesn't then it triggers a docRefPicked action to ensure that it then does match.
       * From that point on the expressions and pickedDocRefs should both be monitoring the same state.
       */
      checkDictionary() {
        const {
          expressionId, term, pickedDocRefs, docRefPicked,
        } = this.props;
        const pickerId = joinDictionaryTermId(expressionId, term.uuid);

        if (term.condition === 'IN_DICTIONARY') {
          // Get the current state of the picked doc refs
          const picked = pickedDocRefs[pickerId];

          // If the dictionary is set on the term, but not set or equal to the 'picked' one, update it
          if (term.dictionary) {
            if (!picked || picked.uuid !== term.dictionary.uuid) {
              docRefPicked(pickerId, term.dictionary);
            }
          }
        }
      }

      render() {
        return <WrappedComponent {...this.props} />;
      }
  };

  return connect(
    state => ({
      // terms are nested, so take all their props from parent
      pickedDocRefs: state.explorerTree.pickedDocRefs,
    }),
    {
      docRefPicked,
    },
  )(WithPickedDocRef);
};

const ExpressionTerm = ({
  connectDragSource,
  isDragging,
  term,
  isEnabled,
  dataSource,
  expressionId,

  requestExpressionItemDelete,
  expressionItemUpdated,
}) => {
  const pickerId = joinDictionaryTermId(expressionId, term.uuid);

  const onTermUpdated = (updates) => {
    expressionItemUpdated(expressionId, term.uuid, updates);
  };

  const onDeleteTerm = () => {
    requestExpressionItemDelete(expressionId, term.uuid);
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
    enabledButton = <Button icon="checkmark" compact basic onClick={onEnabledChange} />;
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
          onChange={onSingleValueChange.bind(this)}
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
            onChange={onFromValueChange.bind(this)}
          />
          <span className="input-between__divider">to</span>
          <Input
            placeholder="to"
            type={valueType}
            value={toValue}
            onChange={onToValueChange.bind(this)}
          />
        </span>
      ); // some between selection
      break;
    }
    case 'IN': {
      const hasValues = !!term.value && term.value.length > 0;
      const splitValues = hasValues ? term.value.split(',') : [];
      const keyedValues = hasValues
        ? splitValues.map(s => ({ key: s, value: s, text: s }))
        : [];
      valueWidget = (
        <Dropdown
          options={keyedValues}
          multiple
          value={splitValues}
          placeholder="type multiple values"
          search={(options, query) => [{ key: query, value: query, text: query }]}
          selection
          onChange={onMultipleValueChange.bind(this)}
        />
      );
      break;
    }
    case 'IN_DICTIONARY': {
      let dictUuid = '';
      if (term.dictionary) {
        dictUuid = term.dictionary.uuid;
      }
      valueWidget = <DocRefModalPicker pickerId={pickerId} typeFilter="dictionary" />;
      break;
    }
  }

  return connectDragSource(<div className={className}>
    <span>
      <Icon color="grey" name="bars" />
    </span>
    <Dropdown
      placeholder="field"
      selection
      options={fieldOptions}
      onChange={onFieldChange.bind(this)}
      value={term.field}
    />
    <Dropdown
      placeholder="condition"
      selection
      options={conditionOptions}
      onChange={onConditionChange.bind(this)}
      value={term.condition}
    />
    {valueWidget}
    <Button.Group floated="right">
      {enabledButton}
      <Button compact icon="trash" onClick={onDeleteTerm.bind(this)} />
    </Button.Group>
                           </div>);
};
ExpressionTerm.propTypes = {
  // Props
  dataSource: PropTypes.object.isRequired, // complete definition of the data source
  expressionId: PropTypes.string.isRequired, // the ID of the overall expression
  term: PropTypes.object.isRequired, // the operator that this particular element is to represent
  isEnabled: PropTypes.bool.isRequired, // a combination of any parent enabled state, and its own

  // Actions
  expressionItemUpdated: PropTypes.func.isRequired,
  requestExpressionItemDelete: PropTypes.func.isRequired,

  // React DnD
  connectDragSource: PropTypes.func.isRequired,
  isDragging: PropTypes.bool.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      expressionItemUpdated,
      requestExpressionItemDelete,
    },
  ),
  DragSource(ItemTypes.TERM, dragSource, dragCollect),
  withPickedDocRef(),
)(ExpressionTerm);
