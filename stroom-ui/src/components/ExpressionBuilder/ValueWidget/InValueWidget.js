import React from 'react';

import { compose, withHandlers, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Dropdown } from 'semantic-ui-react';

import { actionCreators } from '../redux';

const { expressionItemUpdated } = actionCreators;

const enhance = compose(
  connect(undefined, { expressionItemUpdated }),
  withHandlers({
    onMultipleValueChange: ({ expressionItemUpdated, expressionId, term: { uuid } }) => (
      event,
      { value },
    ) => {
      expressionItemUpdated(expressionId, uuid, {
        value: value.join(),
      });
    },
  }),
  withProps(({ term }) => {
    const hasValues = !!term.value && term.value.length > 0;
    const splitValues = hasValues ? term.value.split(',') : [];
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
