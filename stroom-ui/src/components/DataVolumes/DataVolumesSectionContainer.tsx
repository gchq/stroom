import * as React from "react";

import DataVolumesSection from "./DataVolumesSection";
import useDataVolumes from "./api/useDataVolumes";
import FsVolume from "./types/FsVolume";

const DataVolumes: React.FunctionComponent = () => {
  const {
    volumes,
    isLoading,
    createVolume,
    deleteVolume,
    update,
  } = useDataVolumes();

  return (
    <DataVolumesSection
      onVolumeAdd={() => createVolume()}
      onVolumeChange={(volume: FsVolume) => update(volume)}
      onVolumeDelete={(volume) => deleteVolume(volume.id)}
      volumes={volumes}
      isLoading={isLoading}
    />
  );
};

export default DataVolumes;
