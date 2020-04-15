import { DocumentBase, DocRefType } from "./base";

export interface VisualisationDoc extends DocumentBase<"Visualisation"> {
  description?: string;
  functionName?: string;
  scriptRef?: DocRefType;
  settings?: string;
}
