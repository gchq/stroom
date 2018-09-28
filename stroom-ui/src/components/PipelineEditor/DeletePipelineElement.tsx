import * as React from "react";
import { compose, withHandlers } from "recompose";
import { connect } from "react-redux";

import { ThemedConfirm } from "../ThemedConfirm";
import { actionCreators } from "./redux";

import { StoreState as ElementStoreState } from "./redux/elementReducer";
import { StoreStateById as PipelineStatesStoreStateById } from "./redux/pipelineStatesReducer";
import { GlobalStoreState } from "../../startup/reducers";

const {
  pipelineElementDeleteCancelled,
  pipelineElementDeleteConfirmed
} = actionCreators;

export interface Props {
  pipelineId: string;
}

export interface ConnectState {
  elements: ElementStoreState;
  pipelineState: PipelineStatesStoreStateById;
}
export interface ConnectDispatch {
  pipelineElementDeleteCancelled: typeof pipelineElementDeleteCancelled;
  pipelineElementDeleteConfirmed: typeof pipelineElementDeleteConfirmed;
}
export interface WithHandlers {
  onCancelDelete: () => void;
  onConfirmDelete: () => void;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { elements, pipelineStates } }, { pipelineId }) => ({
      pipelineState: pipelineStates[pipelineId],
      elements
    }),
    {
      pipelineElementDeleteCancelled,
      pipelineElementDeleteConfirmed
    }
  ),
  withHandlers({
    onCancelDelete: ({ pipelineElementDeleteCancelled, pipelineId }) => () =>
      pipelineElementDeleteCancelled(pipelineId),
    onConfirmDelete: ({ pipelineElementDeleteConfirmed, pipelineId }) => () =>
      pipelineElementDeleteConfirmed(pipelineId)
  })
);

const DeletePipelineElement = ({
  pipelineState: { pendingElementIdToDelete },
  onConfirmDelete,
  onCancelDelete
}: EnhancedProps) => (
  <ThemedConfirm
    isOpen={!!pendingElementIdToDelete}
    question={`Delete ${pendingElementIdToDelete} from pipeline?`}
    onCancel={onCancelDelete}
    onConfirm={onConfirmDelete}
  />
);

export default enhance(DeletePipelineElement);
