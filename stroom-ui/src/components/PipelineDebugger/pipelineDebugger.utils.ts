import { StoreStateById as PipelineStatesStatePerId } from "../PipelineEditor/redux/pipelineStatesReducer";

export function getNext(
  pipelineState: PipelineStatesStatePerId
): string | undefined {
  const currentElementId = pipelineState.selectedElementId;
  if (currentElementId && pipelineState.pipeline) {
    const nextLink = pipelineState.pipeline.merged.links.add.find(
      link => link.from === currentElementId
    );
    return nextLink ? nextLink.to : currentElementId;
  } else if (!!pipelineState.asTree) {
    return pipelineState.asTree.uuid;
  }
  return undefined;
}

export function getPrevious(
  pipelineState: PipelineStatesStatePerId
): string | undefined {
  const currentElementId = pipelineState.selectedElementId;
  if (currentElementId && pipelineState.pipeline) {
    const previousLink = pipelineState.pipeline.merged.links.add.find(
      link => link.to === currentElementId
    );
    return previousLink ? previousLink.from : currentElementId;
  } else if (!!pipelineState.asTree) {
    return pipelineState.asTree.uuid;
  }
  return undefined;
}
