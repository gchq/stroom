import React from 'react';

import { compose, withHandlers, withProps } from 'recompose';
import { Dropdown } from 'semantic-ui-react';

const enhance = compose(
  withHandlers({
    onMultipleValueChange: ({ onChange }) => (event, { value }) => onChange(value.join()),
  }),
  withProps(({ value }) => {
    const hasValues = !!value && value.length > 0;
    const splitValues = hasValues ? value.split(',') : [];
    const keyedValues = hasValues ? splitValues.map(s => ({ key: s, value: s, text: s })) : [];

    return {
      splitValues,
      keyedValues,
    };
  }),
);

const InValueWidget = ({ splitValues, keyedValues, onMultipleValueChange }) => (
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

export default enhance(InValueWidget);
