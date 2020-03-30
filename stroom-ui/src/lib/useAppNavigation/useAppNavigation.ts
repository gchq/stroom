import * as React from "react";

import { NavigateApp } from "./types";
import useRouter from "lib/useRouter";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import useUrlGenerator from "./useUrlGenerator";
import { WithChromeContext } from "lib/useRouter/BrowserRouter";

const useAppNavigation = (): NavigateApp => {
  const { history: h } = useRouter();
  const { urlPrefix } = React.useContext(WithChromeContext);
  const u = useUrlGenerator(urlPrefix); // just to make all the following rote lines short
  return React.useMemo(
    () => ({
      urlGenerator: u,
      nav: {
        goToActivity: (activityId: string) =>
          h.push(u.goToActivity(activityId)),
        goToApiKey: (id: string) => h.push(u.goToApiKey(id)),
        goToApiKeys: () => h.push(u.goToApiKeys()),
        goToAuthorisationManager: (isGroup: string) =>
          h.push(u.goToAuthorisationManager(isGroup)),
        goToAuthorisationsForDocument: (docRefUuid: string) =>
          h.push(u.goToAuthorisationsForDocument(docRefUuid)),
        goToAuthorisationsForDocumentForUser: (
          docRefUuid: string,
          userUuid: string,
        ) =>
          h.push(u.goToAuthorisationsForDocumentForUser(docRefUuid, userUuid)),
        goToAuthorisationsForUser: (userUuid: string) =>
          h.push(u.goToAuthorisationsForUser(userUuid)),
        goToEditDocRef: (docRef: DocRefType) =>
          h.push(u.goToEditDocRef(docRef)),
        goToEditDocRefByUuid: (docRefUuid: string) =>
          h.push(u.goToEditDocRefByUuid(docRefUuid)),
        goToError: () => h.push(u.goToError()),
        goToDataVolumes: () => h.push(u.goToDataVolumes()),
        goToIndexVolume: (volumeId: string) =>
          h.push(u.goToIndexVolume(volumeId)),
        goToIndexVolumeGroup: (groupName: string) =>
          h.push(u.goToIndexVolumeGroup(groupName)),
        goToIndexVolumeGroups: () => h.push(u.goToIndexVolumeGroups()),
        goToIndexVolumes: () => h.push(u.goToIndexVolumes()),
        goToLogin: () => h.push(u.goToLogin()),
        goToNewApiKey: () => h.push(u.goToNewApiKey()),
        goToNewUser: () => h.push(u.goToNewUser()),
        goToProcessing: () => h.push(u.goToProcessing()),
        goToStreamBrowser: () => h.push(u.goToStreamBrowser()),
        goToUser: (userId: string) => h.push(u.goToUser(userId)),
        goToUserSettings: () => h.push(u.goToUserSettings()),
        goToUsers: () => h.push(u.goToUsers()),
        goToWelcome: () => h.push(u.goToWelcome()),
      },
    }),
    [h, u],
  );
};

export default useAppNavigation;
