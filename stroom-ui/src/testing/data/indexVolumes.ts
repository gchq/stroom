import { loremIpsum } from "lorem-ipsum";
import { IndexVolumeGroup } from "components/IndexVolumes/indexVolumeGroupApi";
import { IndexVolume } from "components/IndexVolumes/indexVolumeApi";

let groupId = 0;
export const generateTestIndexVolumeGroup = (): IndexVolumeGroup => ({
  id: `${groupId++}`,
  name: loremIpsum({ count: 3, units: "words" }),
  createTimeMs: Date.now(),
  updateTimeMs: Date.now(),
  createUser: "admin",
  updateUser: "admin",
});

let id = 0;

export const generateTestIndexVolume = (
  indexVolumeGroupName: string,
): IndexVolume => ({
  id: `${id++}`,
  path: `/data/${loremIpsum({ count: 3, units: "words" })}`,
  indexVolumeGroupName,
  nodeName: "node1",
  createTimeMs: Date.now(),
  updateTimeMs: Date.now(),
  createUser: "admin",
  updateUser: "admin",
  bytesFree: 100,
  bytesUsed: 20,
  bytesLimit: 80,
  bytesTotal: 100,
  statusMs: 0,
});
