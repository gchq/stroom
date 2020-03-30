import { DocumentBase } from "./base";

export type IndexFieldType = "FIELD" | "NUMERIC_FIELD" | "DATE_FIELD" | "ID";

export const IndexFieldTypeDisplayValues = {
  FIELD: "Text",
  NUMERIC_FIELD: "Number",
  DATE_FIELD: "Date",
  ID: "Id",
};

export type AnalyzerType =
  | "KEYWORD"
  | "ALPHA"
  | "NUMERIC"
  | "ALPHA_NUMERIC"
  | "WHITESPACE"
  | "STOP"
  | "STANDARD";

export const AnalyzerDisplayValues = {
  KEYWORD: "Keyword",
  ALPHA: "Alpha",
  NUMERIC: "Numeric",
  ALPHA_NUMERIC: "Alpha numeric",
  WHITESPACE: "Whitespace",
  STOP: "Stop words",
  STANDARD: "Standard",
};

export interface IndexField {
  fieldType: IndexFieldType;
  fieldName: string;
  stored: boolean;
  indexed: boolean;
  termPositions: boolean;
  analyzerType: AnalyzerType;
  caseSensitive: boolean;
}

export interface IndexDoc extends DocumentBase<"Index"> {
  description?: string;
  volumeGroupName?: string;
  fields?: IndexField[];
}
