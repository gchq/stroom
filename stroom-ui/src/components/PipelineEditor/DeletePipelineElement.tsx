import * as React from "react";
import { compose } from "recompose";
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

interface ConnectState {
  elements: ElementStoreState;
  pipelineState: PipelineStatesStoreStateById;
}
interface ConnectDispatch {
  pipelineElementDeleteCancelled: typeof pipelineElementDeleteCancelled;
  pipelineElementDeleteConfirmed: typeof pipelineElementDeleteConfirmed;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

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
  )
);

const DeletePipelineElement = ({
  pipelineState: { pendingElementIdToDelete },
  pipelineElementDeleteCancelled,
  pipelineElementDeleteConfirmed,
  pipelineId
}: EnhancedProps) => {
  const onCancelDelete = () => pipelineElementDeleteCancelled(pipelineId);
  const onConfirmDelete = () => pipelineElementDeleteConfirmed(pipelineId);

  return (
    <ThemedConfirm
      isOpen={!!pendingElementIdToDelete}
      question={`Delete ${pendingElementIdToDelete} from pipeline?`}
      onCloseDialog={onCancelDelete}
      onConfirm={onConfirmDelete}
    />
  );
};

export default enhance(DeletePipelineElement);
