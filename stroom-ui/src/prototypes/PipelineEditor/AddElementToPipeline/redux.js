import { createAction, handleActions } from 'redux-actions';

import { pipelineElementAdded } from '../redux';

const ADD_ELEMENT_STATE = {
  NOT_ADDING: 0,
  PICKING_ELEMENT_DEFINITION: 1,
  PICKING_NAME: 2,
};

const defaultAddElementToPipelineState = {
  addElementState: ADD_ELEMENT_STATE.NOT_ADDING,
  pipelineId: undefined,
  parentId: undefined,
  childDefinition: undefined,
  searchTerm: '',
};

const initiateAddPipelineElement = createAction(
  'INITIATE_ADD_PIPELINE_ELEMENT',
  (pipelineId, elementId) => ({ pipelineId, elementId }),
);

const restartAddPipelineElement = createAction('ADD_PIPELINE_ELEMENT_BACK_TO_CHOOSE');

const choosePipelineElementToAdd = createAction(
  'CHOOSE_PIPELINE_ELEMENT_TO_ADD',
  childDefinition => ({
    childDefinition,
  }),
);

const addElementsearchTermUpdated = createAction('ADD_ELEMENT_SEARCH_TERM_CHANGED', searchTerm => ({
  searchTerm,
}));

const cancelAddPipelineElement = createAction('CANCEL_ADD_PIPELINE_ELEMENT');

const addElementToPipelineWizardReducer = handleActions(
  {
    [initiateAddPipelineElement]: (state, action) => ({
      addElementState: ADD_ELEMENT_STATE.PICKING_ELEMENT_DEFINITION,
      pipelineId: action.payload.pipelineId,
      parentId: action.payload.elementId,
      childDefinition: undefined,
      searchTerm: '',
    }),
    [restartAddPipelineElement]: (state, action) => ({
      ...state,
      addElementState: ADD_ELEMENT_STATE.PICKING_ELEMENT_DEFINITION,
    }),
    [addElementsearchTermUpdated]: (state, action) => ({
      ...state,
      searchTerm: action.payload.searchTerm,
    }),
    [choosePipelineElementToAdd]: (state, action) => ({
      ...state,
      addElementState: ADD_ELEMENT_STATE.PICKING_NAME,
      childDefinition: action.payload.childDefinition,
    }),
    [cancelAddPipelineElement]: (state, action) => defaultAddElementToPipelineState,
    [pipelineElementAdded]: (state, action) => defaultAddElementToPipelineState,
  },
  defaultAddElementToPipelineState,
);

export {
  ADD_ELEMENT_STATE,
  initiateAddPipelineElement,
  restartAddPipelineElement,
  addElementsearchTermUpdated,
  choosePipelineElementToAdd,
  cancelAddPipelineElement,
  addElementToPipelineWizardReducer,
};
