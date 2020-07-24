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
import DataVolumesSection from "./DataVolumesSection";
import FsVolume from "./types/FsVolume";
import JsonDebug from "testing/JsonDebug";

import { useCallback } from "react";

const volume01: FsVolume = {
  id: "1",
  version: 7,
  path: "/some/amazing/path/1",
  status: "ACTIVE",
  byteLimit: 1001,
  volumeState: {
    id: 1,
    version: 1,
    bytesUsed: 54676,
    bytesFree: 674545,
    bytesTotal: 674545,
    updateTimeMs: 56556,
  },
  updateTimeMs: Date.now(),
  updateUser: "Updating user",
  createTimeMs: 234534345,
  createUser: "Creating user",
};

const volume02: FsVolume = {
  id: "2",
  version: 9,
  path: "/some/amazing/path/2",
  status: "CLOSED",
  byteLimit: 1002,
  volumeState: {
    id: 2,
    version: 1,
    bytesUsed: 1278976,
    bytesFree: 2312434,
    bytesTotal: 3453446,
    updateTimeMs: 234342,
  },
  updateTimeMs: Date.now(),
  updateUser: "Updating user",
  createTimeMs: 234534345,
  createUser: "Creating user",
};

const volume03: FsVolume = {
  id: "3",
  version: 1,
  path: "/some/amazing/path/3",
  status: "CLOSED",
  byteLimit: 1003,
  volumeState: {
    id: 3,
    version: 1,
    bytesUsed: 2242323,
    bytesFree: 345345,
    bytesTotal: 2342323,
    updateTimeMs: 2342,
  },
  updateTimeMs: Date.now(),
  updateUser: "Updating user",
  createTimeMs: 234534345,
  createUser: "Creating user",
};

const TestHarness: React.FunctionComponent = () => {
  const initialVolumes = [volume01, volume02, volume03];

  const [volumes, setVolumes] = React.useState<FsVolume[]>(initialVolumes);
  const handleAddVolume = useCallback(() => {}, []);
  // {
  // const newVolumeId = -1;
  // const newVolume: FsVolume = {
  //   id: newVolumeId,
  //   volumePath: "",
  //   createTimeMs: -1,
  //   createUser: "",
  //   updateTimeMs: -1,
  //   updateUser: "",
  // };
  // setVolumes([...volumes, newVolume]);
  // }, [setVolumes, volumes]);

  const handleDeleteVolume = useCallback(
    (volume: FsVolume) => {
      setVolumes(volumes.filter((v) => v.id !== volume.id));
    },
    [setVolumes, volumes],
  );

  const handleVolumeChange = useCallback(
    (fsVolume: FsVolume) => {
      const otherVolumes = volumes.filter((v) => v.id !== fsVolume.id);
      setVolumes([...otherVolumes, fsVolume]);
    },
    [volumes],
  );

  return (
    <div>
      <DataVolumesSection
        volumes={volumes}
        isLoading={false}
        onVolumeAdd={handleAddVolume}
        onVolumeChange={handleVolumeChange}
        onVolumeDelete={handleDeleteVolume}
      />

      <JsonDebug value={{ volumes }} />
    </div>
  );
};

storiesOf("Sections/Data Volumes", module).add("DataVolumesSection", () => (
  <TestHarness />
));
