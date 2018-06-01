import { createAction, handleActions } from 'redux-actions';

const defaultAddElementToPipelineReducer = {};

const initiateAddPipelineElement = createAction(
  'INITIATE_ADD_PIPELINE_ELEMENT',
  (pipelineId, elementId) => ({ pipelineId, elementId }),
);

const choosePipelineElementToAdd = createAction(
  'CHOOSE_PIPELINE_ELEMENT_TO_ADD',
  (pipelineId, pendingElementToAddChildDefinition) => ({
    pipelineId,
    pendingElementToAddChildDefinition,
  }),
);

const addElementSearchTermChanged = createAction(
  'ADD_ELEMENT_SEARCH_TERM_CHANGED',
  (pipelineId, searchTerm) => ({
    pipelineId,
    searchTerm,
  }),
);

const cancelAddPipelineElement = createAction('CANCEL_ADD_PIPELINE_ELEMENT', pipelineId => ({
  pipelineId,
}));

const addElementToPipelineReducer = handleActions(
  {
    [initiateAddPipelineElement]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pendingElementToAddParent: action.payload.elementId,
        pendingElementToAddChildDefinition: undefined,
        searchTerm: '',
      },
    }),
    [addElementSearchTermChanged]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        searchTerm: action.payload.searchTerm,
      },
    }),
    [choosePipelineElementToAdd]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pendingElementToAddChildDefinition: action.payload.pendingElementToAddChildDefinition,
      },
    }),
    [cancelAddPipelineElement]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pendingElementToAddParent: undefined,
        pendingElementToAddChildDefinition: undefined,
        searchTerm: '',
      },
    }),
  },
  defaultAddElementToPipelineReducer,
);

export {
  initiateAddPipelineElement,
  addElementSearchTermChanged,
  choosePipelineElementToAdd,
  cancelAddPipelineElement,
  addElementToPipelineReducer,
};
