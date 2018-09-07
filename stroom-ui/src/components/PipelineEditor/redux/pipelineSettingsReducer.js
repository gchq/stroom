import { createActions, handleActions, combineActions } from 'redux-actions';
import { createActionHandlersPerId } from 'lib/reduxFormUtils';

import { actionCreators as pipelineActionCreators } from './pipelineStatesReducer';

const { pipelineSettingsUpdated } = pipelineActionCreators;

const actionCreators = createActions({
  PIPELINE_SETTINGS_OPENED: pipelineId => ({ pipelineId, isOpen: true }),
  PIPELINE_SETTINGS_CLOSED: pipelineId => ({ pipelineId, isOpen: false }),
});

const { pipelineSettingsOpened, pipelineSettingsClosed } = actionCreators;

const defaultState = {};
const defaultStatePerPipeline = {
  isOpen: false,
};

const byPipelineId = createActionHandlersPerId(
  ({ payload: pipelineId }) => pipelineId,
  defaultStatePerPipeline,
);

const reducer = handleActions(
  byPipelineId({
    [combineActions(pipelineSettingsOpened, pipelineSettingsClosed)]: (
      state,
      { payload: { pipelineId, isOpen } },
    ) => ({
      isOpen,
    }),
    [pipelineSettingsUpdated]: (state, { payload: { pipelineId } }) => ({
      isOpen: false,
    }),
  }),
  defaultState,
);

export { actionCreators, reducer };
