import * as React from "react";
import {
  useDocTypePermissions,
  useDocumentPermissionsForUser,
} from "components/AuthorisationManager/api/docPermission";
import {} from "components/AuthorisationManager/api/docPermission";
import CheckboxSeries from "components/CheckboxSeries";
import Button from "components/Button";
import useRouter from "lib/useRouter";
import { useUser } from "components/AuthorisationManager/api/userGroups";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";

interface Props {
  docRefUuid: string;
  userUuid: string;
}

export const DocumentPermissionForUserEditor: React.FunctionComponent<Props> = ({
  docRefUuid,
  userUuid,
}) => {
  const { history } = useRouter();
  const { findDocRefWithLineage } = useDocumentTree();

  const { node: docRef } = findDocRefWithLineage(docRefUuid);

  const user = useUser(userUuid);
  const permissionsForType = useDocTypePermissions(docRef.type);
  const {
    permissionNames,
    addPermission,
    removePermission,
  } = useDocumentPermissionsForUser(docRefUuid, userUuid);

  return (
    <div>
      <h2>{`Document Permissions for Doc ${docRef.type}-${docRef.name}, user ${
        user && user.name
      }`}</h2>
      <Button onClick={history.goBack}>Back</Button>

      <CheckboxSeries
        allValues={permissionsForType}
        includedValues={permissionNames}
        addValue={addPermission}
        removeValue={removePermission}
      />
    </div>
  );
};

export default DocumentPermissionForUserEditor;
