import { DocumentBase } from "./base";

export interface XsltDoc extends DocumentBase<"XSLT"> {
  description?: string;
  data?: string;
}
