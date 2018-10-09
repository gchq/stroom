import * as React from "react";
import { compose, lifecycle, branch, renderComponent } from "recompose";
import { connect } from "react-redux";
import { GlobalStoreState } from "./reducers";

import Loader from "../components/Loader";
import { fetchConfig, StoreState as ConfigState } from "./config";

export interface Props {}

interface ConnectState {
  config: ConfigState;
}
interface ConnectDispatch {
  fetchConfig: typeof fetchConfig;
}
export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

/**
 * Higher Order Component that kicks off the fetch of the config, and waits by rendering a Loader until
 * that config is returned. This will generally be used by top level components in the app.
 * Once the config has been returned, it then kicks off the various fetch operations for global data.
 */
export default compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ config }) => ({
      config
    }),
    {
      fetchConfig
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}, {}>({
    componentDidMount() {
      this.props.fetchConfig();
    }
  }),
  branch(
    ({ config: { isReady } }) => !isReady,
    renderComponent(() => <Loader message="Awaiting config..." />)
  )
);
