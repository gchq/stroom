import { DocumentBase } from "./base";

export interface ElasticIndexDoc extends DocumentBase<"ElasticIndex"> {
  indexName?: string;
  indexedType?: string;
}
