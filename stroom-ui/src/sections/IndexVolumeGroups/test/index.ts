import * as loremIpsum from "lorem-ipsum";

import { IndexVolumeGroup, IndexVolume } from "src/types";

export const generateTestIndexVolumeGroup = (): IndexVolumeGroup => ({
  name: loremIpsum({ count: 3, units: "words" }),
  createTimeMs: Date.now(),
  updateTimeMs: Date.now(),
  createUser: "admin",
  updateUser: "admin"
});

let id = 0;

export const generateTestIndexVolume = (): IndexVolume => ({
  id: id++,
  path: `/data/${loremIpsum({ count: 3, units: "words" })}`,
  nodeName: "node1",
  createTimeMs: Date.now(),
  updateTimeMs: Date.now(),
  createUser: "admin",
  updateUser: "admin",
  bytesFree: 100,
  bytesUsed: 20,
  bytesLimit: 80,
  bytesTotal: 100,
  statusMs: 0
});
