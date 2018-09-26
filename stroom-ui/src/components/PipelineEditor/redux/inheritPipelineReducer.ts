import { Action, ActionCreator } from "redux";

import { prepareReducer } from "../../../lib/redux-actions-ts";

export const START_INHERITED_PIPELINE = "START_INHERITED_PIPELINE";

export interface StoreState {
  pipelineId?: string;
}

export interface StartInheritPipelineAction
  extends Action<"START_INHERITED_PIPELINE"> {
  pipelineId: string;
}

export interface ActionCreators {
  startInheritPipeline: ActionCreator<StartInheritPipelineAction>;
}

export const actionCreators: ActionCreators = {
  startInheritPipeline: pipelineId => ({
    type: START_INHERITED_PIPELINE,
    pipelineId
  })
};

const defaultState: StoreState = {
  pipelineId: undefined
};

export const reducer = prepareReducer(defaultState)
  .handleAction<StartInheritPipelineAction>(
    START_INHERITED_PIPELINE,
    (state, { pipelineId }) => ({
      pipelineId
    })
  )
  .getReducer();
