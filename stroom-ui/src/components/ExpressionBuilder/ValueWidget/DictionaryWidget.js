import React from 'react';

import { compose, withHandlers } from 'recompose';
import { connect } from 'react-redux';

import { actionCreators } from '../redux';
import AppSearchBar from 'components/AppSearchBar';

const { expressionItemUpdated } = actionCreators;

const enhance = compose(
  connect(undefined, { expressionItemUpdated }),
  withHandlers({
    onDictionaryValueChange: ({
      expressionItemUpdated,
      expressionId,
      term: { uuid },
    }) => (docRef) => {
      expressionItemUpdated(expressionId, uuid, {
        value: docRef,
      });
    },
  }),
);

const DictionaryWidget = ({ term, onDictionaryValueChange }) => (
  <AppSearchBar
    pickerId={term.uuid}
    typeFilters={['Dictionary']}
    onChange={onDictionaryValueChange}
    value={term.value}
  />
);

export default enhance(DictionaryWidget);
