import { createActions, handleActions } from 'redux-actions';

import { groupByCategory, keyByType } from '../elementUtils';

const defaultElementState = {};

const actionCreators = createActions({
  ELEMENTS_RECEIVED: elements => ({ elements }),
  ELEMENT_PROPERTIES_RECEIVED: elementProperties => ({ elementProperties }),
});

const reducer = handleActions(
  {
    ELEMENTS_RECEIVED: (state, action) => ({
      ...state,
      elements: action.payload.elements,
      byCategory: groupByCategory(action.payload.elements),
      byType: keyByType(action.payload.elements)
    }),

    ELEMENT_PROPERTIES_RECEIVED: (state, action) => ({
      ...state,
      elementProperties: action.payload.elementProperties,
    }),
  },
  defaultElementState,
);

export { actionCreators, reducer };
