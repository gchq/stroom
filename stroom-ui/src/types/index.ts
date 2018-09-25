export interface ItemWithId {
  uuid: string;
}

export interface DocRefType extends ItemWithId {
  type: string;
  name?: string;
}

export interface DocRefInfoType {
  docRef: DocRefType;
  createTime: number;
  updateTime: number;
  createUser: string;
  updateUser: string;
  otherInfo: string;
}

export interface Tree<T extends ItemWithId> {
  children?: Array<T & Tree<T>>;
}

export interface DocRefTree extends DocRefType, Tree<DocRefType> {}

export interface TWithLineage<T extends ItemWithId> {
  node: Tree<T> & T;
  lineage: Array<T>;
}

export interface DocRefWithLineage extends TWithLineage<DocRefType> {}

export type DocRefConsumer = (d: DocRefType) => void;

export interface SelectOptionType {
  text: string;
  value: string;
}

export interface OptionType {
  text: string;
  value: string;
}

export interface Dictionary {
  docRef?: DocRefType;
  description?: string;
  data?: string;
  imports?: Array<DocRefType>;
}

export type ConditionType =
  | "EQUALS"
  | "IN"
  | "IN_DICTIONARY"
  | "CONTAINS"
  | "BETWEEN"
  | "GREATER_THAN"
  | "GREATER_THAN_OR_EQUAL_TO"
  | "LESS_THAN"
  | "LESS_THAN_OR_EQUAL_TO";

export interface DataSourceFieldType {
  type: "ID" | "FIELD" | "NUMERIC_FIELD" | "DATE_FIELD";
  name: string;
  queryable: boolean;
  conditions: Array<ConditionType>;
}

export interface DataSourceType {
  fields: Array<DataSourceFieldType>;
}

export interface ExpressionItem {
  uuid?: string;
  type: string;
  enabled: boolean;
}

export interface ExpressionOperator extends ExpressionItem {
  type: "operator";
  op: "AND" | "OR" | "NOT";
  children: Array<ExpressionTerm | ExpressionOperator>;
}

export interface ExpressionTerm extends ExpressionItem {
  type: "term";
  field: string;
  condition: ConditionType;
  value?: any;
  dictionary: Dictionary | null;
}

export interface ElementDefinition {
  type: string;
  category: string;
  roles: Array<string>;
  icon: string;
}
