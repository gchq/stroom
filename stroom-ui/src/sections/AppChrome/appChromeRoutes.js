import React from 'react';

import { Header } from 'semantic-ui-react';

import { withConfig } from 'startup/config';
import { AppChrome } from './index';
import TrackerDashboard from 'sections/TrackerDashboard';
import PipelineEditor, {
  ActionBarItems as PipelineEditorActionBarItems,
  HeaderContent as PipelineEditorHeaderContent,
} from 'components/PipelineEditor';
import XsltEditor, { ActionBarItems as XsltEditorActionBarItems } from 'prototypes/XsltEditor';
import PipelineSearch from 'components/PipelineSearch';
import Welcome from 'sections/Welcome';
import FolderExplorer from 'components/FolderExplorer';
import DocExplorer, { ActionBarItems as DocExplorerActionBarItems } from 'components/DocExplorer';
import DataViewer, { ActionBarItems as DataViewerActionBarItems } from 'components/DataViewer';
import UserSettings from 'prototypes/UserSettings';
import PathNotFound from 'components/PathNotFound';
import IFrame from 'components/IFrame';

const renderWelcome = props => (
  <AppChrome
    activeMenuItem="Welcome"
    {...props}
    headerContent={<Header.Content>Welcome to Stroom</Header.Content>}
    icon="home"
    content={<Welcome />}
  />
);

const UsersIFrame = props => <IFrame key="users" url={props.config.authUsersUiUrl} />;
const UsersIFrameWithConfig = withConfig(UsersIFrame);

const ApiTokensIFrame = props => <IFrame key="apikeys" url={props.config.authTokensUiUrl} />;
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
    path: '/s/docExplorer',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        headerContent={<Header.Content>Explorer</Header.Content>}
        icon="eye"
        content={<DocExplorer explorerId="app-chrome-stories" />}
        actionBarItems={<DocExplorerActionBarItems explorerId="app-chrome-stories" />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/data',
    render: props => (
      <AppChrome
        activeMenuItem="Data"
        headerContent={<Header.Content>Data</Header.Content>}
        icon="database"
        content={<DataViewer dataViewerId="system" />}
        actionBarItems={<DataViewerActionBarItems dataViewerId="system" />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/pipelines',
    render: props => (
      <AppChrome
        activeMenuItem="Pipelines"
        headerContent={<Header.Content>Pipelines</Header.Content>}
        icon="tasks"
        content={<PipelineSearch />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/processing',
    render: props => (
      <AppChrome
        activeMenuItem="Processing"
        headerContent={<Header.Content>Processing</Header.Content>}
        icon="play"
        content={<TrackerDashboard />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/me',
    render: props => (
      <AppChrome
        activeMenuItem="Me"
        headerContent={<Header.Content>Me</Header.Content>}
        icon="user"
        content={<UserSettings />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/users',
    render: props => (
      <AppChrome
        activeMenuItem="Users"
        headerContent={<Header.Content>Users</Header.Content>}
        icon="users"
        content={<UsersIFrameWithConfig />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/apikeys',
    render: props => (
      <AppChrome
        activeMenuItem="API Keys"
        headerContent={<Header.Content>API Keys</Header.Content>}
        icon="key"
        content={<ApiTokensIFrameWithConfig />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/XSLT/:xsltId',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        {...props}
        headerContent={<Header.Content>Edit XSLT</Header.Content>}
        icon="file"
        content={<XsltEditor xsltId={props.match.params.xsltId} />}
        actionBarItems={<XsltEditorActionBarItems xsltId={props.match.params.xsltId} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/Folder/:folderUuid',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        {...props}
        headerContent={<Header.Content>Folder</Header.Content>}
        icon="file"
        content={<FolderExplorer folderUuid={props.match.params.folderUuid} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/System/:folderUuid',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        {...props}
        headerContent={<Header.Content>Folder</Header.Content>}
        icon="file"
        content={<FolderExplorer folderUuid={props.match.params.folderUuid} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/Pipeline/:pipelineId',
    render: props => (
      <AppChrome
        activeMenuItem="Pipelines"
        {...props}
        headerContent={<PipelineEditorHeaderContent pipelineId={props.match.params.pipelineId} />}
        icon="file"
        content={<PipelineEditor pipelineId={props.match.params.pipelineId} />}
        actionBarItems={<PipelineEditorActionBarItems pipelineId={props.match.params.pipelineId} />}
      />
    ),
  },
  {
    exact: true,
    path: '/s/doc/:type/:uuid',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        {...props}
        headerContent={<Header.Content>{`Edit ${props.match.params.type}`}</Header.Content>}
        icon="file"
        content={<PathNotFound message="no editor provided for this doc ref type " />}
      />
    ),
  },
  {
    render: () => (
      <AppChrome
        activeMenuItem="Welcome"
        headerContent={<Header.Content>Not Found</Header.Content>}
        icon="exclamation triangle"
        content={<PathNotFound />}
      />
    ),
  },
];
