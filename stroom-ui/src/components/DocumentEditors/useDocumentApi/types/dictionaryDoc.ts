import { DocumentBase, DocRefType } from "./base";

export interface DictionaryDoc extends DocumentBase<"Dictionary"> {
  description?: string;
  data?: string;
  imports?: DocRefType[];
}
