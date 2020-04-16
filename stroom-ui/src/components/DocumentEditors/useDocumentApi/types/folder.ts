import { DocumentBase, DocRefType } from "./base";

export interface FolderDoc extends DocumentBase<"Folder"> {
  children: DocRefType[];
}
