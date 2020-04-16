import { HasAuditInfo } from "components/DocumentEditors/api/explorer/types";
import FsVolumeState from "./FsVolumeState";

/**
 * Represents a file system volume, matches FsVolume.java
 */
export default interface FsVolume extends HasAuditInfo {
  readonly id: string;
  readonly version: number;
  path: string;
  status: "ACTIVE" | "INACTIVE" | "CLOSED";
  byteLimit: number;
  readonly volumeState: FsVolumeState;
};
