import useLocalStorage, { storeObjectFactory } from "../useLocalStorage";
import { DocRefType, DocRefConsumer } from "src/types";

export interface OutProps {
  recentItems: Array<DocRefType>;
  addRecentItem: DocRefConsumer;
}

export const useRecentItems = (): OutProps => {
  const { setValue, value } = useLocalStorage<Array<DocRefType>>(
    "recent-items",
    [],
    storeObjectFactory()
  );

  return {
    recentItems: value,
    addRecentItem: (d: DocRefType) =>
      setValue([d, ...value.filter(v => v.uuid !== d.uuid)])
  };
};

export default useRecentItems;
