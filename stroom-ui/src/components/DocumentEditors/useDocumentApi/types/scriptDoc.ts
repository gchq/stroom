import { DocumentBase, DocRefType } from "./base";

export interface ScriptDoc extends DocumentBase<"Script"> {
  description?: string;
  dependencies?: DocRefType[];
  data?: string;
}
