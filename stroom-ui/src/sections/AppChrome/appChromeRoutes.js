import React from 'react';

import { Header, Icon } from 'semantic-ui-react';

import { withConfig } from 'startup/config';
import { AppChrome } from './index';
import { TrackerDashboardWithHeader } from 'sections/TrackerDashboard';
import { PipelineEditorWithHeader } from 'components/PipelineEditor';
import { XsltEditorWithHeader } from 'prototypes/XsltEditor';
import { PipelineSearchWithHeader } from 'components/PipelineSearch';
import Welcome from 'sections/Welcome';
import { FolderExplorerWithHeader } from 'components/FolderExplorer';
import { DataViewerWithHeader } from 'components/DataViewer';
import { UserSettingsWithHeader } from 'prototypes/UserSettings';
import PathNotFound from 'components/PathNotFound';
import IFrame from 'components/IFrame';
import { AppSearchWithHeader } from 'prototypes/AppSearch';
import { RecentItemsWithHeader } from 'prototypes/RecentItems';
import WithHeader from 'components/WithHeader';

const renderWelcome = props => <AppChrome activeMenuItem="Welcome" content={<Welcome />} />;

const UsersIFrame = ({ config: { authUsersUiUrl } }) => (
  <WithHeader
    header={
      <Header as="h3">
        <Icon color="grey" name="users" />
        <Header.Content>Users</Header.Content>
      </Header>
    }
    content={<IFrame key="users" url={authUsersUiUrl} />}
  />
);
const UsersIFrameWithConfig = withConfig(UsersIFrame);

const ApiTokensIFrame = ({ config: { authTokensUiUrl } }) => (
  <WithHeader
    header={
      <Header as="h3">
        <Icon color="grey" name="key" />
        <Header.Content>API Keys</Header.Content>
      </Header>
    }
    content={<IFrame key="apikeys" url={authTokensUiUrl} />}
  />
);
const ApiTokensIFrameWithConfig = withConfig(ApiTokensIFrame);

export default [
  {
    exact: true,
    path: '/',
    render: renderWelcome,
  },
  {
    exact: true,
    path: '/s/welcome',
    render: renderWelcome,
  },
  {
    exact: true,
    path: '/s/data',
    render: props => (
      <AppChrome activeMenuItem="Data" content={<DataViewerWithHeader dataViewerId="system" />} />
    ),
  },
  {
    exact: true,
    path: '/s/pipelines',
    render: props => (
      <AppChrome activeMenuItem="Pipelines" content={<PipelineSearchWithHeader />} />
    ),
  },
  {
    exact: true,
    path: '/s/processing',
    render: props => (
      <AppChrome activeMenuItem="Processing" content={<TrackerDashboardWithHeader />} />
    ),
  },
  {
    exact: true,
    path: '/s/me',
    render: props => <AppChrome activeMenuItem="Me" content={<UserSettingsWithHeader />} />,
  },
  {
    exact: true,
    path: '/s/users',
    render: props => <AppChrome activeMenuItem="Users" content={<UsersIFrameWithConfig />} />,
  },
  {
    exact: true,
    path: '/s/apikeys',
    render: props => (
      <AppChrome activeMenuItem="API Keys" content={<ApiTokensIFrameWithConfig />} />
    ),
  },
  {
    exact: true,
    path: '/s/search',
    render: props => <AppChrome activeMenuItem="Search" content={<AppSearchWithHeader />} />,
  },
  {
    exact: true,
    path: '/s/recentItems',
    render: props => (
      <AppChrome activeMenuItem="Recent Items" content={<RecentItemsWithHeader />} />
    ),
  },
  {
    exact: true,
    path: '/s/doc/XSLT/:xsltId',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        content={<XsltEditorWithHeader xsltId={props.match.params.xsltId} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/Folder/:folderUuid',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        content={<FolderExplorerWithHeader folderUuid={props.match.params.folderUuid} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/System/:folderUuid',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        content={<FolderExplorerWithHeader folderUuid={props.match.params.folderUuid} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/Pipeline/:pipelineId',
    render: props => (
      <AppChrome
        activeMenuItem="Pipelines"
        content={<PipelineEditorWithHeader pipelineId={props.match.params.pipelineId} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/:type/:uuid',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        content={<PathNotFound message="no editor provided for this doc ref type " />}
      />
    ),
  },
  {
    render: () => <AppChrome activeMenuItem="Welcome" content={<PathNotFound />} />,
  },
];
