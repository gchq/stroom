import { DocumentBase } from "./base";

export interface XMLSchemaDoc extends DocumentBase<"XMLSchema"> {
  description?: string;
  namespaceURI?: string;
  systemId?: string;
  data?: string;
  deprecated?: boolean;
  schemaGroup?: string;
}
