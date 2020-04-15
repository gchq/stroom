import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import * as React from "react";
import { RawNavigateApp } from "./types";

const useUrlGenerator = (
  urlPrefix: string,
): RawNavigateApp<string | undefined, string> => {
  return React.useMemo(
    () => ({
      goToActivity: (activityId: string = ":activityId") =>
        `/${urlPrefix}/activity/${activityId}`,
      goToApiKey: (id: string) => `/${urlPrefix}/apikey/${id}`,
      goToApiKeys: () => `/${urlPrefix}/apikeys`,
      goToAuthorisationManager: (isGroup: string = ":isGroup") =>
        `/${urlPrefix}/authorisationManager/${isGroup}`,
      goToAuthorisationsForDocument: (docRefUuid: string = ":docRefUuid") =>
        `/${urlPrefix}/authorisationManager/document/${docRefUuid}`,
      goToAuthorisationsForDocumentForUser: (
        docRefUuid: string = ":docRefUuid",
        userUuid: string = ":userUuid",
      ) =>
        `/${urlPrefix}/authorisationManager/document/${docRefUuid}/${userUuid}`,
      goToAuthorisationsForUser: (userUuid: string = ":userUuid") =>
        `/${urlPrefix}/authorisationManager/${userUuid}`,
      goToEditDocRef: (docRef: DocRefType) =>
        `/${urlPrefix}/doc/${docRef.uuid}`,
      goToEditDocRefByUuid: (docRefUuid: string = ":docRefUuid") =>
        `/${urlPrefix}/doc/${docRefUuid}`,
      goToError: () => `/${urlPrefix}/error`,
      goToDataVolumes: () => `/${urlPrefix}/data/volumes`,
      goToIndexVolume: (volumeId: string = ":volumeId") =>
        `/${urlPrefix}/indexing/volumes/${volumeId}`,
      goToIndexVolumeGroup: (groupName: string = ":groupName") =>
        `/${urlPrefix}/indexing/groups/${groupName}`,
      goToIndexVolumeGroups: () => `/${urlPrefix}/indexing/groups`,
      goToIndexVolumes: () => `/${urlPrefix}/indexing/volumes`,
      goToLogin: () => `${urlPrefix}/login`,
      goToNewApiKey: () => `/${urlPrefix}/apikey/new`,
      goToNewUser: () => `/${urlPrefix}/user/new`,
      goToProcessing: () => `/${urlPrefix}/processing`,
      goToStreamBrowser: () => `/${urlPrefix}/data`,
      goToUser: (userId: string) => `/${urlPrefix}/user/${userId}`,
      goToUserSettings: () => `/${urlPrefix}/me`,
      goToUsers: () => `/${urlPrefix}/users`,
      goToWelcome: () => `/${urlPrefix}/welcome`,
    }),
    [urlPrefix],
  );
};

export default useUrlGenerator;
