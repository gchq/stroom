export interface BuildInfo {
  upDate: string;
  buildDate: string;
  buildVersion: string;
}

export interface SessionInfo {
  userName: string;
  nodeName: string;
  buildInfo: BuildInfo;
}
