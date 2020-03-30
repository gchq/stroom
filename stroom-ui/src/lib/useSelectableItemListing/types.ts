import { TableProps } from "react-table";

import { SelectionBehaviour } from "./enums";

export interface InProps<TItem> {
  getKey: (x: TItem) => string;
  items: TItem[];
  openItem?: (i: TItem) => void;
  enterItem?: (i: TItem) => void;
  goBack?: (i: TItem) => void;
  selectionBehaviour?: SelectionBehaviour;
  /**
   * This function allows the owner to be notified when the focus is about to wrap.
   * It must return a boolean to indicate if the wrap should take place.
   * If it should not take place, the assumption is that the owner will trigger a request
   * for more data.
   */
  preFocusWrap?: () => boolean;
}

export interface OutProps<TItem> {
  focusIndex: number;
  highlightedItem?: any;
  selectedIndexes: number[];
  lastSelectedIndex?: number;
  selectedItem?: TItem;
  selectedItems: TItem[];
  toggleSelection: (itemKey: string) => void;
  clearSelection: () => void;
  onKeyDown: React.KeyboardEventHandler<HTMLDivElement>;
}

export interface TableOutProps<TItem> extends OutProps<TItem> {
  tableProps: Partial<TableProps>;
}
