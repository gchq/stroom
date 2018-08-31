import React from 'react';
import { compose, lifecycle, branch, renderComponent } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react';

import { fetchDocTree } from 'components/FolderExplorer/explorerClient';

/**
 * Higher Order Component that kicks off the fetch of the config, and waits by rendering a Loader until
 * that config is returned. This will generally be used by top level components in the app.
 * Once the config has been returned, it then kicks off the various fetch operations for global data.
 */
export default compose(
  connect(({ folderExplorer: { documentTree } }) => ({ documentTree }), {
    fetchDocTree,
  }),
  lifecycle({
    componentDidMount() {
      this.props.fetchDocTree();
    },
  }),
  branch(
    ({ documentTree }) => documentTree.waitingForTree,
    renderComponent(() => <Loader active>Loading document tree</Loader>),
  ),
);
