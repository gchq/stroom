import { Tree, TWithLineage } from "lib/treeUtils";

export interface DocRefType {
  uuid: string;
  type: string;
  name?: string;
}

export const copyDocRef = (input: DocRefType): DocRefType => {
  return {
    uuid: input.uuid,
    type: input.type,
    name: input.name,
  };
};

export interface DocRefInfoType {
  docRef: DocRefType;
  createTime: number;
  updateTime: number;
  createUser: string;
  updateUser: string;
  otherInfo: string;
}

export interface DocRefTree extends DocRefType, Tree<DocRefType> {}

export type DocRefWithLineage = TWithLineage<DocRefType>;

export type DocRefConsumer = (d: DocRefType) => void;

export interface DocumentBase<T extends string> {
  uuid: string;
  type: T;
  name?: string;
}
