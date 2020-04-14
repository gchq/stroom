import * as React from "react";

import IndexVolumesSection from "./IndexVolumesSection";
import { useIndexVolumes, IndexVolume } from "./indexVolumeApi";
import { useIndexVolumeGroups, IndexVolumeGroup } from "./indexVolumeGroupApi";

const IndexVolumes: React.FunctionComponent = () => {
  const {
    indexVolumes,
    createIndexVolume,
    deleteIndexVolume,
    update,
    refresh: refreshIndexVolumes,
  } = useIndexVolumes();

  const {
    groups,
    createIndexVolumeGroup,
    update: updateIndexVolumeGroup,
    deleteIndexVolumeGroup,
  } = useIndexVolumeGroups();

  return (
    <IndexVolumesSection
      onGroupAdd={() => createIndexVolumeGroup()}
      onGroupChange={(indexVolumeGroup: IndexVolumeGroup) =>
        updateIndexVolumeGroup(indexVolumeGroup).then(() => refreshIndexVolumes())
      }
      onGroupDelete={(id: string) => deleteIndexVolumeGroup(id)}
      onVolumeAdd={indexVolumeGroupName =>
        createIndexVolume({ indexVolumeGroupName })
      }
      onVolumeChange={(indexVolume: IndexVolume) => update(indexVolume)}
      onVolumeDelete={id => deleteIndexVolume(id)}
      indexVolumeGroups={groups}
      indexVolumes={indexVolumes}
    />
  );
};

export default IndexVolumes;
