import * as React from "react";
import { compose } from "recompose";
import { connect } from "react-redux";
import { RenderFunction } from "@storybook/react";
import { Provider } from "react-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";
import StoryRouter from "storybook-react-router";

import createStore from "../../startup/store";

import { GlobalStoreState } from "../../startup/reducers";

import { setupTestServer } from "../../lib/storybook/PollyDecorator";

import FontAwesomeProvider from "../../startup/FontAwesomeProvider";
import KeyIsDown from "../../lib/KeyIsDown";
import testData from "./fullTestData";

interface Props {}
interface ConnectState {
  theme: string;
}
interface ConnectDispatch {}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhanceLocal = compose(
  setupTestServer(testData),
  DragDropContext(HTML5Backend),
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userSettings: { theme } }) => ({
      theme
    }),
    {}
  ),
  KeyIsDown(),
  FontAwesomeProvider
);

const store = createStore();

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

export default (storyFn: RenderFunction) => (
  <Provider store={store}>
    <ThemedComponent>{StoryRouter()(storyFn)}</ThemedComponent>
  </Provider>
);
