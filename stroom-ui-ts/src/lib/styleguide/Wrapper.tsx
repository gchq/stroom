import * as React from "react";
import { compose } from "recompose";
import { connect } from "react-redux";
import { Provider } from "react-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import FontAwesomeProvider from "../../startup/FontAwesomeProvider";
import KeyIsDown from "../../lib/KeyIsDown";
import store from "../../startup/store";

const WrappedComponent = ({ children, theme }) => (
  <Provider store={store}>
    <div className={`app-container ${theme}`}>{children}</div>
  </Provider>
);

const enhance = compose(
  DragDropContext(HTML5Backend),
  FontAwesomeProvider,
  KeyIsDown(),
  connect(
    ({ userSettings: { theme } }) => ({
      theme
    }),
    {}
  )
);

const Wrapper = enhance(WrappedComponent);

export default Wrapper;
