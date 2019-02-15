import * as React from "react";
import * as ReactDOM from "react-dom";

import { toClass, compose } from "recompose";
import { Provider } from "react-redux";
import { StoreContext } from "redux-react-hook";
import { ConnectedRouter } from "react-router-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import Routes from "./startup/Routes";
import createStore from "./startup/store";
import FontAwesomeProvider from "./startup/FontAwesomeProvider";
import { history } from "./startup/middleware";

import "./styles/main.css";
import { ThemeContextProvider } from "./lib/theme";

const DndRoutes = compose(
  FontAwesomeProvider,
  DragDropContext(HTML5Backend),
  toClass
)(Routes);

const store = createStore();

ReactDOM.render(
  <StoreContext.Provider value={store}>
    <Provider store={store}>
      <ThemeContextProvider>
        <ConnectedRouter history={history}>
          <DndRoutes />
        </ConnectedRouter>
      </ThemeContextProvider>
    </Provider>
  </StoreContext.Provider>,
  document.getElementById("root") as HTMLElement
);
