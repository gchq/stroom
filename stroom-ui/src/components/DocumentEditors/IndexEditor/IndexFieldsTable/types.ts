import { IndexField } from "components/DocumentEditors/useDocumentApi/types/indexDoc";
import { TableOutProps } from "lib/useSelectableItemListing/types";

export interface Props {
  fields: IndexField[];
  selectableTableProps: TableOutProps<IndexField>;
}

export interface UseTable {
  componentProps: Props;
}
