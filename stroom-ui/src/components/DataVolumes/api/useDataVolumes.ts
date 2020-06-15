import * as React from "react";

import useApi from "./useApi";
import useListReducer from "lib/useListReducer";
import FsVolume from "../types/FsVolume";

/**
 * Convenience function for using Index Volume.
 * This hook connects the REST API calls to the Redux Store.
 */
interface UseDataVolumes {
  volumes: FsVolume[];
  isLoading: boolean;
  update: (volume: FsVolume) => Promise<void>;
  createVolume: () => void;
  deleteVolume: (id: string) => void;
  refresh: () => void;
}

const useDataVolumes = (): UseDataVolumes => {
  const {
    items: volumes,
    updateItemAtIndex,
    receiveItems,
    addItem,
    removeItem,
  } = useListReducer<FsVolume>((iv) => iv.id);

  const { getVolumes, deleteVolume, createVolume, update } = useApi();
  const [isLoading, setIsLoading] = React.useState<boolean>(true);
  React.useEffect(() => {
    getVolumes().then((items) => {
      receiveItems(items);
      setIsLoading(false);
    });
  }, [getVolumes, isLoading, setIsLoading, receiveItems]);

  return {
    volumes,
    isLoading,
    createVolume: React.useCallback(() => createVolume().then(addItem), [
      addItem,
      createVolume,
    ]),
    deleteVolume: React.useCallback(
      (id: string) => deleteVolume(id).then(() => removeItem(id)),
      [removeItem, deleteVolume],
    ),
    update: React.useCallback(
      (volume: FsVolume) =>
        update(volume).then((response) => {
          const indexToUpdate = volumes.findIndex(
            (item) => item.id === response.id,
          );
          return updateItemAtIndex(indexToUpdate, response);
        }),
      [update, updateItemAtIndex, volumes],
    ),
    refresh: React.useCallback(() => getVolumes().then(receiveItems), [
      getVolumes,
      receiveItems,
    ]),
  };
};

export default useDataVolumes;
