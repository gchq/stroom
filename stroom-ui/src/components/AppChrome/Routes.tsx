/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import { Route, RouteComponentProps, Switch } from "react-router";
import { PrivateRoute } from "startup/Authentication";
import AuthorisationManager, {
  UserAuthorisationEditor,
} from "../AuthorisationManager";
import DocumentPermissionEditor from "components/AuthorisationManager/DocumentPermissionEditor";
import DocumentPermissionForUserEditor from "components/AuthorisationManager/DocumentPermissionForUserEditor";
import MetaBrowser from "components/MetaBrowser";
import SwitchedDocRefEditor from "components/DocumentEditors/SwitchedDocRefEditor";
import ErrorPage from "components/ErrorPage";
import IndexVolumes from "components/IndexVolumes";
import {
  ResetPasswordContainer,
  ResetPasswordRequestContainer,
  ConfirmPasswordResetEmailContainer,
} from "components/password";
import PathNotFound from "components/PathNotFound";
import { Processing } from "components/Processing";
import { CreateToken } from "components/tokens";
import { EditToken } from "components/tokens/Create";
import TokenSearch from "components/tokens/Search/SearchToken";
// import { UserCreate, UserEdit, UserSearch } from "components/Account";
import UserSettings from "components/UserSettings";
import { Welcome } from "components/Welcome";
import AppChrome from "./AppChrome";
import { useAppNavigation } from "lib/useAppNavigation";
import DataVolumes from "components/DataVolumes/DataVolumesSectionContainer";
import SignInManager from "../Authentication/SignInManager";
import StroomWrapper from "../Authentication/StroomWrapper";

const renderWelcome = ({
  match: {
    params: { urlPrefix },
  },
}: RouteComponentProps<{ urlPrefix: string }>) => (
  <AppChrome
    activeMenuItem="welcome"
    urlPrefix={urlPrefix}
    content={<Welcome />}
  />
);

/**
 * This doesn't have to be 's', but that's the convention we've
 * settled on.
 */
const singlePagePrefix = "/s";

const Routes: React.FunctionComponent = () => {
  const { urlGenerator } = useAppNavigation();
  return (
    <Switch>
      <Route path={`${singlePagePrefix}/stroom`} component={StroomWrapper} />
      <Route exact path={`${singlePagePrefix}/error`} component={ErrorPage} />
      <Route
        exact
        path={`${singlePagePrefix}/openWelcome`}
        component={Welcome}
      />
      <Route
        exact
        path={`${singlePagePrefix}/resetPassword`}
        component={ResetPasswordContainer}
      />
      <Route
        exact
        path={`${singlePagePrefix}/resetPasswordRequest`}
        component={ResetPasswordRequestContainer}
      />
      <Route
        exact
        path={`${singlePagePrefix}/confirmPasswordResetEmail`}
        component={ConfirmPasswordResetEmailContainer}
      />

      <Route
        exact
        path={`${singlePagePrefix}/signIn`}
        component={SignInManager}
      />
      <PrivateRoute exact path="/" render={renderWelcome} />
      <PrivateRoute
        exact
        path={urlGenerator.goToWelcome()}
        render={renderWelcome}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToStreamBrowser()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="data"
            urlPrefix={urlPrefix}
            content={<MetaBrowser />}
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToProcessing()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="processing"
            urlPrefix={urlPrefix}
            content={<Processing />}
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToUserSettings()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="userSettings"
            urlPrefix={urlPrefix}
            content={<UserSettings />}
          />
        )}
      />
      {[false, true].map((isGroup) => (
        <PrivateRoute
          key={isGroup ? "Group" : "User"}
          exact
          path={urlGenerator.goToAuthorisationManager(isGroup.toString())}
          render={({
            match: {
              params: { urlPrefix },
            },
          }) => (
            <AppChrome
              activeMenuItem={isGroup ? "groupPermissions" : "userPermissions"}
              urlPrefix={urlPrefix}
              content={<AuthorisationManager isGroup={isGroup} />}
            />
          )}
        />
      ))}

      <PrivateRoute
        exact
        path={urlGenerator.goToAuthorisationsForUser(undefined)}
        render={({
          match: {
            params: { urlPrefix, userUuid },
          },
        }: RouteComponentProps<any>) => (
          <AppChrome
            activeMenuItem="userPermissions"
            urlPrefix={urlPrefix}
            content={<UserAuthorisationEditor userUuid={userUuid} />}
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToAuthorisationsForDocument(undefined)}
        render={({
          match: {
            params: { urlPrefix, docRefUuid },
          },
        }: RouteComponentProps<any>) => (
          <AppChrome
            activeMenuItem="userPermissions"
            urlPrefix={urlPrefix}
            content={<DocumentPermissionEditor docRefUuid={docRefUuid} />}
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToAuthorisationsForDocumentForUser(
          undefined,
          undefined,
        )}
        render={({
          match: {
            params: { urlPrefix, userUuid, docRefUuid },
          },
        }: RouteComponentProps<any>) => (
          <AppChrome
            activeMenuItem="userPermissions"
            urlPrefix={urlPrefix}
            content={
              <DocumentPermissionForUserEditor
                userUuid={userUuid}
                docRefUuid={docRefUuid}
              />
            }
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToDataVolumes()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="dataVolumes"
            urlPrefix={urlPrefix}
            content={<DataVolumes />}
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToIndexVolumes()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="indexVolumes"
            urlPrefix={urlPrefix}
            content={<IndexVolumes />}
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToError()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="welcome"
            urlPrefix={urlPrefix}
            content={<ErrorPage />}
          />
        )}
      />
      <PrivateRoute
        exact
        path={urlGenerator.goToEditDocRefByUuid(undefined)}
        render={({
          match: {
            params: { urlPrefix, docRefUuid },
          },
        }: RouteComponentProps<any>) => (
          <AppChrome
            activeMenuItem={docRefUuid}
            urlPrefix={urlPrefix}
            content={<SwitchedDocRefEditor docRefUuid={docRefUuid} />}
          />
        )}
      />

      {/*<PrivateRoute*/}
      {/*  exact*/}
      {/*  path={urlGenerator.goToUsers()}*/}
      {/*  render={({*/}
      {/*    match: {*/}
      {/*      params: { urlPrefix },*/}
      {/*    },*/}
      {/*  }) => (*/}
      {/*    <AppChrome*/}
      {/*      urlPrefix={urlPrefix}*/}
      {/*      activeMenuItem="userIdentities"*/}
      {/*      content={<UserSearch />}*/}
      {/*    />*/}
      {/*  )}*/}
      {/*/>*/}
      {/*<PrivateRoute*/}
      {/*  exact*/}
      {/*  path={urlGenerator.goToNewUser()}*/}
      {/*  render={({*/}
      {/*    match: {*/}
      {/*      params: { urlPrefix },*/}
      {/*    },*/}
      {/*  }) => (*/}
      {/*    <AppChrome*/}
      {/*      urlPrefix={urlPrefix}*/}
      {/*      activeMenuItem="userIdentities"*/}
      {/*      content={<UserCreate />}*/}
      {/*    />*/}
      {/*  )}*/}
      {/*/>*/}
      {/*<PrivateRoute*/}
      {/*  exact*/}
      {/*  path={urlGenerator.goToUser(":userId")}*/}
      {/*  render={({*/}
      {/*    match: {*/}
      {/*      params: { urlPrefix },*/}
      {/*    },*/}
      {/*  }) => (*/}
      {/*    <AppChrome*/}
      {/*      activeMenuItem="userIdentities"*/}
      {/*      content={<UserEdit />}*/}
      {/*      urlPrefix={urlPrefix}*/}
      {/*    />*/}
      {/*  )}*/}
      {/*/>*/}

      <PrivateRoute
        exact
        path={urlGenerator.goToApiKeys()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="apiKeys"
            urlPrefix={urlPrefix}
            content={<TokenSearch />}
          />
        )}
      />

      <PrivateRoute
        exact
        path={urlGenerator.goToNewApiKey()}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            urlPrefix={urlPrefix}
            activeMenuItem="apiKeys"
            content={<CreateToken />}
          />
        )}
      />

      <PrivateRoute
        exact
        path={urlGenerator.goToApiKey(":id")}
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            urlPrefix={urlPrefix}
            activeMenuItem="apiKeys"
            content={<EditToken />}
          />
        )}
      />

      <PrivateRoute
        render={({
          match: {
            params: { urlPrefix },
          },
        }) => (
          <AppChrome
            activeMenuItem="welcome"
            urlPrefix={urlPrefix}
            content={<PathNotFound />}
          />
        )}
      />

      {/* Default route */}
      <Route render={() => <PathNotFound message="Invalid path" />} />
    </Switch>
  );
};

export default Routes;
