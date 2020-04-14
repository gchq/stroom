import { IndexVolumeGroup } from "./indexVolumeGroupApi";
import { IndexVolume } from "./indexVolumeApi";

export const indexVolumeGroup01: IndexVolumeGroup = {
  name: "Index volume group 01",
  id: "1",
  createTimeMs: Date.now(),
  updateTimeMs: Date.now(),
  createUser: "Test user",
  updateUser: "Updating user",
};

export const indexVolumeGroup02: IndexVolumeGroup = {
  name: "Index volume group 02",
  id: "2",
  createTimeMs: Date.now(),
  updateTimeMs: Date.now(),
  createUser: "Test user",
  updateUser: "Updating user",
};

export const indexVolume01: IndexVolume = {
  nodeName: "Index volume name 01",
  path: "/some/amazing/path",
  bytesFree: 1,
  bytesLimit: 1,
  bytesTotal: 1,
  bytesUsed: 1,
  createTimeMs: Date.now(),
  createUser: "Creating user",
  id: "1",
  indexVolumeGroupName: "Group name 01",
  statusMs: Date.now(),
  updateTimeMs: Date.now(),
  updateUser: "Updating user",
};

export const indexVolume02: IndexVolume = {
  nodeName: "Index volume name 02",
  path: "/some/amazing/path",
  bytesFree: 1,
  bytesLimit: 1,
  bytesTotal: 1,
  bytesUsed: 1,
  createTimeMs: Date.now(),
  createUser: "Creating user",
  id: "2",
  indexVolumeGroupName: "Group name 01",
  statusMs: Date.now(),
  updateTimeMs: Date.now(),
  updateUser: "Updating user",
};

export const indexVolume03: IndexVolume = {
  nodeName: "Index volume name 03",
  path: "/some/amazing/path",
  bytesFree: 1,
  bytesLimit: 1,
  bytesTotal: 1,
  bytesUsed: 1,
  createTimeMs: Date.now(),
  createUser: "Creating user",
  id: "3",
  indexVolumeGroupName: "Group name 02",
  statusMs: Date.now(),
  updateTimeMs: Date.now(),
  updateUser: "Updating user",
};
