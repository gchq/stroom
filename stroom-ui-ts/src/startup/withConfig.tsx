import * as React from "react";
import { compose, lifecycle, branch, renderComponent } from "recompose";
import { connect } from "react-redux";
import { GlobalStoreState } from "./reducers";

import Loader from "../components/Loader";
import { fetchConfig } from "./config";

export interface Props {
  fetchConfig: typeof fetchConfig;
}

/**
 * Higher Order Component that kicks off the fetch of the config, and waits by rendering a Loader until
 * that config is returned. This will generally be used by top level components in the app.
 * Once the config has been returned, it then kicks off the various fetch operations for global data.
 */
export default compose(
  connect(
    ({ config }: GlobalStoreState) => ({
      config
    }),
    {
      fetchConfig
    }
  ),
  lifecycle<Props, {}, {}>({
    componentDidMount() {
      this.props.fetchConfig();
    }
  }),
  branch(
    ({ config: { isReady } }) => !isReady,
    renderComponent(() => <Loader message="Awaiting config..." />)
  )
);
