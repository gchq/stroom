import * as React from "react";
import * as ReactDOM from "react-dom";

import { toClass, compose } from "recompose";
import { Provider } from "react-redux";
import { ConnectedRouter } from "react-router-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import KeyIsDown from "./lib/KeyIsDown";
import Routes from "./startup/Routes";
import store from "./startup/store";
import FontAwesomeProvider from "./startup/FontAwesomeProvider";
import { history } from "./startup/middleware";

import "styles/main.css";

const DndRoutes = compose(
  FontAwesomeProvider,
  KeyIsDown(),
  DragDropContext(HTML5Backend),
  toClass
)(Routes);

ReactDOM.render(
  <Provider store={store}>
    <ConnectedRouter history={history}>
      <DndRoutes />
    </ConnectedRouter>
  </Provider>,
  document.getElementById("root") as HTMLElement
);
