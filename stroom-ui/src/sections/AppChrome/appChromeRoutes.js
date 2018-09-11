import React from 'react';
import { connect } from 'react-redux';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Header, Grid } from 'semantic-ui-react/dist/commonjs';

import { AppChrome } from '.';
import { Processing } from 'sections/Processing';
import DocEditor from 'components/DocEditor';
import Welcome from 'sections/Welcome';
import DataViewer from 'sections/DataViewer';
import UserSettings from 'sections/UserSettings';
import PathNotFound from 'components/PathNotFound';
import IFrame from 'components/IFrame';
import ErrorPage from 'components/ErrorPage';

const renderWelcome = props => <AppChrome activeMenuItem="Welcome" content={<Welcome />} />;

const withConfig = connect(({ config }) => ({ config }));

const UsersIFrame = withConfig(({ config: { authUsersUiUrl } }) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={12}>
        <Header as="h3">
          <FontAwesomeIcon icon="users" />
          <Header.Content>Users</Header.Content>
        </Header>
      </Grid.Column>
    </Grid>
    <IFrame key="users" url={authUsersUiUrl} />
  </React.Fragment>
));

const ApiTokensIFrame = withConfig(({ config: { authTokensUiUrl } }) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={12}>
        <Header as="h3">
          <FontAwesomeIcon icon="key" />
          <Header.Content>API Keys</Header.Content>
        </Header>
      </Grid.Column>
    </Grid>
    <IFrame key="apikeys" url={authTokensUiUrl} />
  </React.Fragment>
));

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
    path: '/s/processing',
    render: props => <AppChrome activeMenuItem="Processing" content={<Processing />} />,
  },
  {
    exact: true,
    path: '/s/me',
    render: props => <AppChrome activeMenuItem="Me" content={<UserSettings />} />,
  },
  {
    exact: true,
    path: '/s/users',
    render: props => <AppChrome activeMenuItem="Users" content={<UsersIFrame />} />,
  },
  {
    exact: true,
    path: '/s/apikeys',
    render: props => <AppChrome activeMenuItem="API Keys" content={<ApiTokensIFrame />} />,
  },
  {
    exact: true,
    path: '/s/error',
    render: props => <AppChrome activeMenuItem="Error" content={<ErrorPage />} />,
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
