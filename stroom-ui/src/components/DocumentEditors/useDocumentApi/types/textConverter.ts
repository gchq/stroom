import { DocumentBase } from "./base";

export type TextConverterType = "None" | "Data Splitter" | "XML Fragment";
export interface TextConverterDoc extends DocumentBase<"TextConverter"> {
  description?: string;
  data?: string;
  converterType?: TextConverterType;
}
