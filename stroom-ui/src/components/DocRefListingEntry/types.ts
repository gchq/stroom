import {
  DocRefType,
  DocRefConsumer,
} from "components/DocumentEditors/useDocumentApi/types/base";

export interface Props {
  docRef: DocRefType;
  dndIsOver?: boolean;
  dndCanDrop?: boolean;
  openDocRef: DocRefConsumer;
  enterFolder?: DocRefConsumer;
  children?: React.ReactNode;
  toggleSelection: (itemKey: string) => void;
  selectedDocRefs: DocRefType[];
  highlightedDocRef?: DocRefType;
}
