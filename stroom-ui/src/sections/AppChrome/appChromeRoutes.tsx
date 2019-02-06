import * as React from "react";
import { connect } from "react-redux";

import { AppChrome } from ".";
import { Processing } from "../Processing";
import SwitchedDocRefEditor from "../../components/SwitchedDocRefEditor";
import IconHeader from "../../components/IconHeader";
import Welcome from "../Welcome";
import DataViewer from "../DataViewer";
import UserSettings from "../UserSettings";
import PathNotFound from "../../components/PathNotFound";
import IFrame from "../../components/IFrame";
import ErrorPage from "../../components/ErrorPage";

import { Config } from "../../startup/config";
import { GlobalStoreState } from "../../startup/reducers";
import { RouteComponentProps, RouteProps } from "react-router";
import UserPermissions from "../UserPermissions";
import IndexVolumes from "../IndexVolumes";
import IndexVolumeGroups from "../IndexVolumeGroups";
import IndexVolumeGroupEditor from "../../components/IndexVolumeGroupEditor";
import UserPermissionEditor from "../../components/UserPermissionEditor";

const renderWelcome = () => (
  <AppChrome activeMenuItem="Welcome" content={<Welcome />} />
);

interface ConnectState {
  config: Config;
}
interface ConnectDispatch {}

interface WithConfig extends ConnectState, ConnectDispatch {}

const withConfig = connect<ConnectState, ConnectDispatch, {}, GlobalStoreState>(
  ({ config: { values } }) => ({ config: values })
);

const UsersIFrame = withConfig(({ config: { authUsersUiUrl } }: WithConfig) => (
  <React.Fragment>
    <IconHeader icon="users" text="Users" />
    {authUsersUiUrl ? (
      <IFrame key="users" url={authUsersUiUrl} />
    ) : (
      <div>No Users URL in Config</div>
    )}
  </React.Fragment>
));

const ApiTokensIFrame = withConfig(
  ({ config: { authTokensUiUrl } }: WithConfig) => (
    <React.Fragment>
      <IconHeader icon="key" text="API keys" />
      {authTokensUiUrl ? (
        <IFrame key="apikeys" url={authTokensUiUrl} />
      ) : (
        <div>No Api Keys URL in Config</div>
      )}
    </React.Fragment>
  )
);

export default [
  {
    exact: true,
    path: "/",
    render: renderWelcome
  },
  {
    exact: true,
    path: "/s/welcome",
    render: renderWelcome
  },
  {
    exact: true,
    path: "/s/data",
    render: () => (
      <AppChrome
        activeMenuItem="Data"
        content={<DataViewer dataViewerId="system" />}
      />
    )
  },
  {
    exact: true,
    path: "/s/processing",
    render: () => (
      <AppChrome activeMenuItem="Processing" content={<Processing />} />
    )
  },
  {
    exact: true,
    path: "/s/me",
    render: () => <AppChrome activeMenuItem="Me" content={<UserSettings />} />
  },
  {
    exact: true,
    path: "/s/userPermissions",
    render: () => (
      <AppChrome
        activeMenuItem="User Permissions"
        content={<UserPermissions />}
      />
    )
  },
  {
    exact: true,
    path: "/s/userPermissions/:userUuid",
    render: (props: RouteComponentProps<any>) => (
      <AppChrome
        activeMenuItem="User Permissions"
        content={
          <UserPermissionEditor
            userUuid={props.match.params.userUuid}
            listingId="app-chrome"
          />
        }
      />
    )
  },
  {
    exact: true,
    path: "/s/indexing/volumes",
    render: () => (
      <AppChrome activeMenuItem="Index Volumes" content={<IndexVolumes />} />
    )
  },
  {
    exact: true,
    path: "/s/indexing/groups",
    render: () => (
      <AppChrome
        activeMenuItem="Index Groups"
        content={<IndexVolumeGroups />}
      />
    )
  },
  {
    exact: true,
    path: "/s/indexing/groups/:name",
    render: (props: RouteComponentProps<any>) => (
      <AppChrome
        activeMenuItem="Index Groups"
        content={<IndexVolumeGroupEditor name={props.match.params.name} />}
      />
    )
  },
  {
    exact: true,
    path: "/s/users",
    render: () => <AppChrome activeMenuItem="Users" content={<UsersIFrame />} />
  },
  {
    exact: true,
    path: "/s/apikeys",
    render: () => (
      <AppChrome activeMenuItem="API Keys" content={<ApiTokensIFrame />} />
    )
  },
  {
    exact: true,
    path: "/s/error",
    render: () => <AppChrome activeMenuItem="Error" content={<ErrorPage />} />
  },
  {
    exact: true,
    path: "/s/doc/:type/:uuid",
    render: (props: RouteComponentProps<any>) => (
      <AppChrome
        activeMenuItem="Explorer"
        content={<SwitchedDocRefEditor docRef={{ ...props.match.params }} />}
      />
    )
  },
  {
    render: () => (
      <AppChrome activeMenuItem="Welcome" content={<PathNotFound />} />
    )
  }
] as Array<RouteProps>;
