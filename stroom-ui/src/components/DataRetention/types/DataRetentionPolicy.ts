import { DataRetentionRule } from "./DataRetentionRule";

export interface DataRetentionPolicy {
  name: string;
  rules: DataRetentionRule[];
  type: string;
  updateTime: string;
  updateUser: string;
  uuid: string;
  version: string;
}
