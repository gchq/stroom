export interface Tree<T> {
  children?: (T & Tree<T>)[];
}

export interface TWithLineage<T> {
  node: Tree<T> & T;
  lineage: T[];
}
