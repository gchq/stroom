import { createActions, handleActions } from 'redux-actions';

const defaultElementState = {};

const actionCreators = createActions({
  ELEMENTS_RECEIVED: elements => ({ elements }),
  ELEMENT_PROPERTIES_RECEIVED: elementProperties => ({ elementProperties }),
});

const elementReducer = handleActions(
  {
    ELEMENTS_RECEIVED: (state, action) => ({
      ...state,
      elements: action.payload.elements,
    }),

    ELEMENT_PROPERTIES_RECEIVED: (state, action) => ({
      ...state,
      elementProperties: action.payload.elementProperties,
    }),
  },
  defaultElementState,
);

export { actionCreators, elementReducer };
