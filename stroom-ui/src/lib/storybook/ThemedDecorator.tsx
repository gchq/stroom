import * as React from "react";
import { connect } from "react-redux";
import { RenderFunction } from "@storybook/react";
import { GlobalStoreState } from "../../startup/reducers";

interface Props {}
interface ConnectState {
  theme: string;
}
interface ConnectDispatch {}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhanceLocal = connect<
  ConnectState,
  ConnectDispatch,
  Props,
  GlobalStoreState
>(
  ({ userSettings: { theme } }) => ({
    theme
  }),
  {}
);

class WrappedComponent extends React.Component<EnhancedProps> {
  render() {
    return (
      <div className={`app-container ${this.props.theme}`}>
        {this.props.children}
      </div>
    );
  }
}

const ThemedComponent = enhanceLocal(WrappedComponent);

export const ThemedDecorator = (storyFn: RenderFunction) => (
  <ThemedComponent>{storyFn()}</ThemedComponent>
);
