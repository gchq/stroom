import { StroomUser } from "components/AuthorisationManager/api/userGroups";
import { StreamAttributeMapResult } from "components/MetaBrowser/types";
import {
  ElementDefinition,
  ElementPropertiesByElementIdType,
} from "components/DocumentEditors/PipelineEditor/useElements/types";
import {
  DocRefTree,
  DocumentBase,
} from "components/DocumentEditors/useDocumentApi/types/base";
import { ResourcesByDocType } from "components/DocumentEditors/useDocumentApi/types/resourceUrls";
import { DataSourceType } from "components/ExpressionBuilder/types";
import { IndexVolumeGroup } from "components/IndexVolumes/indexVolumeGroupApi";
import { IndexVolume } from "components/IndexVolumes/indexVolumeApi";
import { StreamTaskType } from "components/Processing/types";
import { Account } from "components/users/types";

export interface UserGroupMembership {
  userUuid: string;
  groupUuid: string;
}

export interface UserDocPermission {
  userUuid: string;
  docRefUuid: string;
  permissionName: string;
}

export interface TestData {
  docRefTypes: string[];
  documentTree: DocRefTree;
  elements: ElementDefinition[];
  elementProperties: ElementPropertiesByElementIdType;
  documents: {
    [docRefType in keyof ResourcesByDocType]: DocumentBase<docRefType>[]
  };
  trackers: StreamTaskType[];
  dataList: StreamAttributeMapResult;
  dataSource: DataSourceType;
  usersAndGroups: {
    users: StroomUser[];
    userGroupMemberships: UserGroupMembership[];
  };
  indexVolumesAndGroups: {
    volumes: IndexVolume[];
    groups: IndexVolumeGroup[];
  };
  allAppPermissions: string[];
  userAppPermissions: {
    [userUuid: string]: string[];
  };
  docPermissionByType: {
    [docType: string]: string[];
  };
  users: Account[];
  userDocPermission: UserDocPermission[];
}
