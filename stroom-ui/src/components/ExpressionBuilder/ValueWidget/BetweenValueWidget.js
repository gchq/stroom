import React from 'react';

import { compose, withHandlers, withProps } from 'recompose';
import { connect } from 'react-redux';
import { actionCreators } from '../redux';
import { Input } from 'semantic-ui-react';

import withValueType from './withValueType';

const { expressionItemUpdated } = actionCreators;

const enhance = compose(
  connect(undefined, { expressionItemUpdated }),
  withHandlers({
    onFromValueChange: ({ expressionItemUpdated, expressionId, term }) => (event, data) => {
      const parts = term.value.split(',');
      const existingToValue = parts.length === 2 ? parts[1] : undefined;
      const newValue = `${data.value},${existingToValue}`;

      expressionItemUpdated(expressionId, term.uuid, {
        value: newValue,
      });
    },

    onToValueChange: ({ expressionItemUpdated, expressionId, term }) => (event, data) => {
      const parts = term.value.split(',');
      const existingFromValue = parts.length === 2 ? parts[0] : undefined;
      const newValue = `${existingFromValue},${data.value}`;

      expressionItemUpdated(expressionId, term.uuid, {
        value: newValue,
      });
    },
  }),
  withProps(({ term }) => {
    let fromValue='';
    let toValue='';
    if (term.value) {
      const splitValues = term.value.split(',');
      fromValue = splitValues.length === 2 ? splitValues[0] : undefined;
      toValue = splitValues.length === 2 ? splitValues[1] : undefined;
    }
    
    return {
      fromValue,
      toValue,
    };
  }),
  withValueType,
);

const BetweenValueWidget = ({
  fromValue,
  toValue,
  onFromValueChange,
  onToValueChange,
  valueType,
}) => (
  <span>
    <Input placeholder="from" type={valueType} value={fromValue} onChange={onFromValueChange} />
    <span className="input-between__divider">to</span>
    <Input placeholder="to" type={valueType} value={toValue} onChange={onToValueChange} />
  </span>
);

export default enhance(BetweenValueWidget);
