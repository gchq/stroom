/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import { storiesOf } from "@storybook/react";
import IndexVolumesSection from "../IndexVolumesSection";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import JsonDebug from "testing/JsonDebug";
import {
  indexVolume01,
  indexVolumeGroup01,
  indexVolumeGroup02,
  indexVolume02,
  indexVolume03,
} from "../testData";
import { IndexVolumeGroup } from "../indexVolumeGroupApi";
import { useCallback } from "react";
import { IndexVolume } from "../indexVolumeApi";
const stories = storiesOf("Sections/Index Volumes 2", module);

const TestHarness: React.FunctionComponent = () => {
  var initialGroups = [indexVolumeGroup01, indexVolumeGroup02];
  var initialVolumes = [indexVolume01, indexVolume02, indexVolume03];

  const [groups, setGroups] = React.useState<IndexVolumeGroup[]>(initialGroups);
  const handleAddGroup = useCallback(() => {
    const newGroup: IndexVolumeGroup = {
      id: "-1",
      name: "New group",
      createTimeMs: -1,
      createUser: "",
      updateTimeMs: -1,
      updateUser: "",
    };
    setGroups([...groups, newGroup]);
  }, [setGroups, groups]);

  const [volumes, setVolumes] = React.useState<IndexVolume[]>(initialVolumes);
  const handleAddVolume = useCallback(
    (indexVolumeGroupName: string) => {
      const newVolumeId = "-1";
      const newVolume: IndexVolume = {
        id: newVolumeId,
        indexVolumeGroupName,
        path: "",
        nodeName: "New volume",
        bytesLimit: -1,
        bytesUsed: -1,
        bytesFree: -1,
        bytesTotal: -1,
        statusMs: -1,
        createTimeMs: -1,
        createUser: "",
        updateTimeMs: -1,
        updateUser: "",
      };
      setVolumes([...volumes, newVolume]);
    },
    [setVolumes, volumes],
  );

  const handleDeleteVolume = useCallback(
    (volumeId: string) => {
      setVolumes(volumes.filter(v => v.id !== volumeId));
    },
    [setVolumes, volumes],
  );

  const handleVolumeChange = useCallback(
    (indexVolume: IndexVolume) => {
      const otherVolumes = volumes.filter(v => v.id !== indexVolume.id);
      setVolumes([...otherVolumes, indexVolume]);
    },
    [volumes],
  );

  return (
    <div>
      <IndexVolumesSection
        indexVolumeGroups={groups}
        indexVolumes={volumes}
        onGroupAdd={handleAddGroup}
        onGroupChange={() => console.log("onGroupChange")}
        onGroupDelete={() => console.log("onGroupDelete")}
        onVolumeAdd={handleAddVolume}
        onVolumeChange={handleVolumeChange}
        onVolumeDelete={handleDeleteVolume}
      />

      <JsonDebug value={{ groups }} />
      <JsonDebug value={{ volumes }} />
    </div>
  );
};

addThemedStories(stories, () => <TestHarness />);
