export interface DocumentApi<T> {
  fetchDocument: (uuid: string) => Promise<T>;
  saveDocument: (document: T) => Promise<void>;
}
