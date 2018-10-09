import * as React from "react";
import { compose, lifecycle, branch, renderComponent } from "recompose";
import { connect } from "react-redux";

import { GlobalStoreState } from "../../startup/reducers";
import Loader from "../Loader";
import { fetchDocTree } from "./explorerClient";
import { DocRefTree } from "../../types";

export interface Props {}

interface ConnectState {
  documentTree: DocRefTree;
}

interface ConnectDispatch {
  fetchDocTree: typeof fetchDocTree;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

/**
 * Higher Order Component that kicks off the fetch of the config, and waits by rendering a Loader until
 * that config is returned. This will generally be used by top level components in the app.
 * Once the config has been returned, it then kicks off the various fetch operations for global data.
 */
export default compose<Props, EnhancedProps>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { documentTree } }) => ({
      documentTree
    }),
    {
      fetchDocTree
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      this.props.fetchDocTree();
    }
  }),
  branch(
    ({ documentTree }) => documentTree.waitingForTree,
    renderComponent(() => <Loader message="Loading document tree..." />)
  )
);
