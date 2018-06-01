import { createAction, handleActions } from 'redux-actions';

const defaultElementState = {};

const elementsReceived = createAction('ELEMENTS_RECEIVED', elements => ({ elements }));
const elementPropertiesReceived = createAction(
  'ELEMENT_PROPERTIES_RECEIVED',
  elementProperties => ({ elementProperties }),
);

const elementReducer = handleActions(
  {
    [elementsReceived]: (state, action) => ({
      ...state,
      elements: action.payload.elements,
    }),

    [elementPropertiesReceived]: (state, action) => ({
      ...state,
      elementProperties: action.payload.elementProperties,
    }),
  },
  defaultElementState,
);

export { elementsReceived, elementPropertiesReceived, elementReducer };
