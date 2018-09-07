import { createActions, handleActions } from 'redux-actions';

import { groupByCategory, keyByType } from '../elementUtils';

const defaultElementState = {};

const actionCreators = createActions({
  ELEMENTS_RECEIVED: elements => ({ elements }),
  ELEMENT_PROPERTIES_RECEIVED: elementProperties => ({ elementProperties }),
});

const reducer = handleActions(
  {
    ELEMENTS_RECEIVED: (state, { payload: { elements } }) => ({
      ...state,
      elements,
      byCategory: groupByCategory(elements),
      byType: keyByType(elements),
    }),

    ELEMENT_PROPERTIES_RECEIVED: (state, { payload: { elementProperties } }) => ({
      ...state,
      elementProperties,
    }),
  },
  defaultElementState,
);

export { actionCreators, reducer };
