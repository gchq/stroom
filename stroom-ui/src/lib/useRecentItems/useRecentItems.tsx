import * as React from "react";
import useLocalStorage, { useStoreObjectFactory } from "../useLocalStorage";
import {
  DocRefType,
  DocRefConsumer,
} from "components/DocumentEditors/useDocumentApi/types/base";

interface OutProps {
  recentItems: DocRefType[];
  addRecentItem: DocRefConsumer;
}

const DEFAULT_RECENT_ITEMS: DocRefType[] = [];

export const useRecentItems = (): OutProps => {
  const { reduceValue, value } = useLocalStorage<DocRefType[]>(
    "recent-items",
    DEFAULT_RECENT_ITEMS,
    useStoreObjectFactory(),
  );

  const addRecentItem = React.useCallback(
    (d: DocRefType) =>
      reduceValue((existingValue) => [
        d,
        ...existingValue.filter((v) => v.uuid !== d.uuid),
      ]),
    [reduceValue],
  );

  return {
    recentItems: value,
    addRecentItem,
  };
};

export default useRecentItems;
