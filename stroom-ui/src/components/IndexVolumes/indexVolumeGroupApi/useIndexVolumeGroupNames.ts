import * as React from "react";

import useApi from "./useApi";

const useIndexVolumeGroupNames = (): string[] => {
  const [groupNames, setGroupNames] = React.useState<string[]>([]);
  const { getIndexVolumeGroupNames } = useApi();

  React.useEffect(() => {
    getIndexVolumeGroupNames().then(setGroupNames);
  }, [getIndexVolumeGroupNames, setGroupNames]);

  return groupNames;
};

export default useIndexVolumeGroupNames;
