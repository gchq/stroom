export interface PermissionsByUser {
  [userUuid: string]: string[];
}

export interface PermissionsByUuid {
  [userUuid: string]: string[];
}

export interface DocumentPermissions {
  docRefUuid: string;
  userPermissions: PermissionsByUuid;
}
