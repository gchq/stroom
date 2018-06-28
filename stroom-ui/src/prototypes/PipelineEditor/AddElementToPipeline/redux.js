import { createActions, handleActions } from 'redux-actions';

import { actionCreators as pipelineActionCreators } from '../redux';

const { pipelineElementAdded } = pipelineActionCreators;

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

const actionCreators = createActions({
  INITIATE_ADD_PIPELINE_ELEMENT: (pipelineId, elementId) => ({ pipelineId, elementId }),
  RESTART_ADD_PIPELINE_ELEMENT: () => {},
  CHOOSE_PIPELINE_ELEMENT_TO_ADD: childDefinition => ({
    childDefinition,
  }),
  ADD_ELEMENT_SEARCH_TERM_CHANGED: searchTerm => ({
    searchTerm,
  }),
  CANCEL_ADD_PIPELINE_ELEMENT: () => {},
});

const addElementToPipelineWizardReducer = handleActions(
  {
    INITIATE_ADD_PIPELINE_ELEMENT: (state, action) => ({
      addElementState: ADD_ELEMENT_STATE.PICKING_ELEMENT_DEFINITION,
      pipelineId: action.payload.pipelineId,
      parentId: action.payload.elementId,
      childDefinition: undefined,
      searchTerm: '',
    }),
    RESTART_ADD_PIPELINE_ELEMENT: (state, action) => ({
      ...state,
      addElementState: ADD_ELEMENT_STATE.PICKING_ELEMENT_DEFINITION,
    }),
    ADD_ELEMENT_SEARCH_TERM_CHANGED: (state, action) => ({
      ...state,
      searchTerm: action.payload.searchTerm,
    }),
    CHOOSE_PIPELINE_ELEMENT_TO_ADD: (state, action) => ({
      ...state,
      addElementState: ADD_ELEMENT_STATE.PICKING_NAME,
      childDefinition: action.payload.childDefinition,
    }),
    CANCEL_ADD_PIPELINE_ELEMENT: (state, action) => defaultAddElementToPipelineState,
    [pipelineElementAdded]: (state, action) => defaultAddElementToPipelineState,
  },
  defaultAddElementToPipelineState,
);

export { ADD_ELEMENT_STATE, actionCreators, addElementToPipelineWizardReducer };
