import React from 'react';
import { compose, lifecycle, branch, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react';

import { fetchDocTree, fetchDocRefTypes } from 'components/FolderExplorer/explorerClient';
import { fetchConfig } from './config';

/**
 * Higher Order Component that kicks off the fetch of the config, and waits by rendering a Loader until
 * that config is returned. This will generally be used by top level components in the app.
 * Once the config has been returned, it then kicks off the various fetch operations for global data.
 */
export default compose(
  connect(
    ({ config }, props) => ({
      config,
    }),
    {
      fetchConfig,
      fetchDocTree,
      fetchDocRefTypes,
    },
  ),
  lifecycle({
    componentDidMount() {
      this.props.fetchConfig();
    },
  }),
  branch(
    ({ config: { isReady } }) => !isReady,
    renderComponent(() => <Loader active>Awaiting Config</Loader>),
  ),
  lifecycle({
    componentDidMount() {
      this.props.fetchDocTree();
      this.props.fetchDocRefTypes();
    },
  }),
);
