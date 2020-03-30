/**
 * FsVolumeState is read only, and matches FsVolumeState.java
 */
export default interface FsVolumeState {
  readonly id: number;
  readonly version: number;
  readonly bytesUsed: number;
  readonly bytesFree: number;
  readonly bytesTotal: number;
  readonly updateTimeMs: number;
};
