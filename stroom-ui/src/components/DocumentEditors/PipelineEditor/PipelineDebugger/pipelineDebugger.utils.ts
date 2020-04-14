import { PipelineDocumentType } from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";
import { PipelineAsTreeType } from "../AddElementModal/types";

export function getNext(
  selectedElementId?: string,
  pipeline?: PipelineDocumentType,
  asTree?: PipelineAsTreeType,
): string | undefined {
  if (selectedElementId && pipeline) {
    const nextLink =
      pipeline.merged.links.add &&
      pipeline.merged.links.add.find(link => link.from === selectedElementId);
    return nextLink ? nextLink.to : selectedElementId;
  } else if (!!asTree) {
    return asTree.uuid;
  }
  return undefined;
}

export function getPrevious(
  selectedElementId?: string,
  pipeline?: PipelineDocumentType,
  asTree?: PipelineAsTreeType,
): string | undefined {
  if (selectedElementId && pipeline) {
    const previousLink =
      pipeline.merged.links.add &&
      pipeline.merged.links.add.find(link => link.to === selectedElementId);
    return previousLink ? previousLink.from : selectedElementId;
  } else if (!!asTree) {
    return asTree.uuid;
  }
  return undefined;
}
