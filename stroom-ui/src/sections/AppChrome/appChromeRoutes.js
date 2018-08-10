import React from 'react';

import { Header, Icon } from 'semantic-ui-react';

import { withConfig } from 'startup/config';
import { AppChrome } from './index';
import TrackerDashboard from 'sections/TrackerDashboard';
import DocEditor from 'components/DocEditor';
import PipelineSearch from 'components/PipelineSearch';
import Welcome from 'sections/Welcome';
import DataViewer from 'components/DataViewer';
import UserSettings from 'prototypes/UserSettings';
import PathNotFound from 'components/PathNotFound';
import IFrame from 'components/IFrame';
import AppSearch from 'prototypes/AppSearch';
import RecentItems from 'prototypes/RecentItems';
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
      <AppChrome activeMenuItem="Data" content={<DataViewer dataViewerId="system" />} />
    ),
  },
  {
    exact: true,
    path: '/s/pipelines',
    render: props => <AppChrome activeMenuItem="Pipelines" content={<PipelineSearch />} />,
  },
  {
    exact: true,
    path: '/s/processing',
    render: props => <AppChrome activeMenuItem="Processing" content={<TrackerDashboard />} />,
  },
  {
    exact: true,
    path: '/s/me',
    render: props => <AppChrome activeMenuItem="Me" content={<UserSettings />} />,
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
    render: props => <AppChrome activeMenuItem="Search" content={<AppSearch />} />,
  },
  {
    exact: true,
    path: '/s/recentItems',
    render: props => <AppChrome activeMenuItem="Recent Items" content={<RecentItems />} />,
  },
  {
    exact: true,
    path: '/s/doc/:type/:uuid',
    render: props => (
      <AppChrome
        activeMenuItem="Explorer"
        content={<DocEditor docRef={{ ...props.match.params }} />}
      />
    ),
  },
  {
    render: () => <AppChrome activeMenuItem="Welcome" content={<PathNotFound />} />,
  },
];
