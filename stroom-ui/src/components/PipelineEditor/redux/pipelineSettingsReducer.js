import { createActions, handleActions, combineActions } from 'redux-actions';

import { actionCreators as pipelineActionCreators } from './pipelineStatesReducer';

const { pipelineSettingsUpdated } = pipelineActionCreators;

const actionCreators = createActions({
  PIPELINE_SETTINGS_OPENED: pipelineId => ({ pipelineId, isOpen: true }),
  PIPELINE_SETTINGS_CLOSED: pipelineId => ({ pipelineId, isOpen: false }),
});

const { pipelineSettingsOpened, pipelineSettingsClosed } = actionCreators;

const defaultState = {};

const reducer = handleActions(
  {
    [combineActions(pipelineSettingsOpened, pipelineSettingsClosed)]: (
      state,
      { payload: { pipelineId, isOpen } },
    ) => ({
      ...state,
      [pipelineId]: {
        ...state[pipelineId],
        isOpen,
      },
    }),
    [pipelineSettingsUpdated]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        isOpen: false,
      },
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
