import * as React from "react";
import { compose } from "recompose";
import { RenderFunction } from "@storybook/react";
import { Provider } from "react-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";
import StoryRouter from "storybook-react-router";

import createStore from "../../startup/store";

import { setupTestServer } from "../../lib/storybook/PollyDecorator";

import FontAwesomeProvider from "../../startup/FontAwesomeProvider";
import testData from "./fullTestData";
import { ThemeContextProvider, useTheme } from "../theme";

interface Props {}

const enhanceLocal = compose(
  setupTestServer(testData),
  DragDropContext(HTML5Backend),
  FontAwesomeProvider
);

const store = createStore();

const WrappedComponent: React.StatelessComponent<Props> = props => {
  const { theme } = useTheme();

  return <div className={`app-container ${theme}`}>{props.children}</div>;
};

const ThemedComponent = enhanceLocal(WrappedComponent);

export default (storyFn: RenderFunction) => (
  <Provider store={store}>
    <ThemeContextProvider>
      <ThemedComponent>{StoryRouter()(storyFn)}</ThemedComponent>
    </ThemeContextProvider>
  </Provider>
);
