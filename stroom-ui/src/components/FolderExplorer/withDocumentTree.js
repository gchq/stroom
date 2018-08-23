import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { fetchDocTree } from 'components/FolderExplorer/explorerClient';

/**
 * Higher Order Component that kicks off the fetch of the config, and waits by rendering a Loader until
 * that config is returned. This will generally be used by top level components in the app.
 * Once the config has been returned, it then kicks off the various fetch operations for global data.
 */
export default compose(
  connect(undefined, {
    fetchDocTree,
  }),
  lifecycle({
    componentDidMount() {
      this.props.fetchDocTree();
    },
  }),
);
