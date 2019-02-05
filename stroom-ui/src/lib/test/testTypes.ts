import {
  DocRefTree,
  DocRefTypeList,
  PipelineModelType,
  ElementDefinitions,
  ElementPropertiesByElementIdType,
  Dictionary,
  DataSourceType,
  StreamTaskType,
  User,
  IndexVolume,
  IndexVolumeGroup,
  IndexVolumeGroupMembership,
  IndexDoc,
  XsltDoc
} from "../../types";
import { StreamAttributeMapResult } from "../../sections/DataViewer/types";

export interface UserGroupMembership {
  userUuid: string;
  groupUuid: string;
}

export interface TestData {
  docRefTypes: DocRefTypeList;
  documentTree: DocRefTree;
  pipelines: {
    [pipelineId: string]: PipelineModelType;
  };
  elements: ElementDefinitions;
  elementProperties: ElementPropertiesByElementIdType;
  xslt: {
    [xsltId: string]: XsltDoc;
  };
  dictionaries: {
    [dictionaryId: string]: Dictionary;
  };
  indexes: {
    [indexUuid: string]: IndexDoc;
  };
  trackers: Array<StreamTaskType>;
  dataList: StreamAttributeMapResult;
  dataSource: DataSourceType;
  usersAndGroups: {
    users: Array<User>;
    userGroupMemberships: Array<UserGroupMembership>;
  };
  indexVolumesAndGroups: {
    volumes: Array<IndexVolume>;
    groups: Array<IndexVolumeGroup>;
    groupMemberships: Array<IndexVolumeGroupMembership>;
  };
}
