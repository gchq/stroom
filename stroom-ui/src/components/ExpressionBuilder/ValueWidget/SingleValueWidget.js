import React from 'react';

import { compose, withHandlers } from 'recompose';
import { connect } from 'react-redux';
import { Input } from 'semantic-ui-react';

import { actionCreators } from '../redux';

import withValueType from './withValueType';

const { expressionItemUpdated } = actionCreators;

const enhance = compose(
  connect(undefined, { expressionItemUpdated }),
  withHandlers({
    onSingleValueChange: ({ expressionItemUpdated, expressionId, term: { uuid } }) => (
      event,
      { value },
    ) => {
      expressionItemUpdated(expressionId, uuid, {
        value,
      });
    },
  }),
  withValueType,
);

const SingleValueWidget = ({ term, onSingleValueChange, valueType }) => (
  <Input
    placeholder="value"
    type={valueType}
    value={term.value || ''}
    onChange={onSingleValueChange}
  />
);

export default enhance(SingleValueWidget);
