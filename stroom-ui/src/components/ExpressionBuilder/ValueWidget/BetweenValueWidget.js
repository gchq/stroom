import React from 'react';

import { compose, withHandlers, withProps } from 'recompose';
import { Input } from 'semantic-ui-react';

const enhance = compose(
  withHandlers({
    onFromValueChange: ({ onChange, value = '' }) => (event, data) => {
      const parts = value.split(',');
      const existingToValue = parts.length === 2 ? parts[1] : '';
      const newValue = `${data.value},${existingToValue}`;

      onChange(newValue);
    },

    onToValueChange: ({ onChange, value = '' }) => (event, data) => {
      const parts = value.split(',');
      const existingFromValue = parts.length === 2 ? parts[0] : '';
      const newValue = `${existingFromValue},${data.value}`;

      onChange(newValue);
    },
  }),
  withProps(({ value }) => {
    let fromValue = '';
    let toValue = '';
    if (value) {
      const splitValues = value.split(',');
      fromValue = splitValues.length === 2 ? splitValues[0] : '';
      toValue = splitValues.length === 2 ? splitValues[1] : '';
    }

    return {
      fromValue,
      toValue,
    };
  }),
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
