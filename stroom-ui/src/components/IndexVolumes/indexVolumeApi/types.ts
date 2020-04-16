import { HasAuditInfo } from "components/DocumentEditors/api/explorer/types";

export interface NewIndexVolume {
  path?: string;
  nodeName?: string;
  indexVolumeGroupName: string;
}

export interface IndexVolume extends HasAuditInfo {
  id: string;
  indexVolumeGroupName: string;
  path: string;
  nodeName: string;
  bytesLimit: number;
  bytesUsed: number;
  bytesFree: number;
  bytesTotal: number;
  statusMs: number;
}

export interface UpdateIndexVolumeDTO {
  id: string;
  indexVolumeGroupName: string;
  path: string;
  nodeName: string;
}
