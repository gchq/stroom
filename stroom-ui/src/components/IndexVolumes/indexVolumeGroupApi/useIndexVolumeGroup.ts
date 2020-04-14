import * as React from "react";

import useApi from "./useApi";
import { IndexVolumeGroup } from "./types";

interface UseIndexVolumeGroup {
  indexVolumeGroup: IndexVolumeGroup | undefined;
}

const useIndexVolumeGroup = (groupName: string): UseIndexVolumeGroup => {
  const [indexVolumeGroup, setIndexVolumeGroup] = React.useState<
    IndexVolumeGroup | undefined
  >(undefined);

  const { getIndexVolumeGroup } = useApi();

  React.useEffect(() => {
    getIndexVolumeGroup(groupName).then(setIndexVolumeGroup);
  }, [getIndexVolumeGroup, groupName]);

  return {
    indexVolumeGroup,
  };
};

export default useIndexVolumeGroup;
