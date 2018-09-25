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
