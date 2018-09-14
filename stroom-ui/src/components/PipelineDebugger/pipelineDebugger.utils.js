
export function getNext(pipelineState) {
  const currentElementId = pipelineState.selectedElementId;
  if (currentElementId) {
    const nextLink = pipelineState.pipeline.merged.links.add.find(link => link.from === currentElementId);
    return nextLink ? nextLink.to : currentElementId;
  }
  else {
    return pipelineState.asTree.uuid;
  }
}

export function getPrevious(pipelineState) {
  const currentElementId = pipelineState.selectedElementId;
  if (currentElementId) {
    const previousLink = pipelineState.pipeline.merged.links.add.find(link => link.to === currentElementId);
    return previousLink ? previousLink.from : currentElementId;

  }
  else {
    return pipelineState.asTree.uuid;
  }
}