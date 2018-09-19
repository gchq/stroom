import * as React from "react";
import * as PropTypes from "prop-types";
import { compose, withContext } from "recompose";
//import { connect } from "react-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import FontAwesomeProvider from "../../startup/FontAwesomeProvider";
import KeyIsDown from "../../lib/KeyIsDown";
import createStore from "../../startup/store";

export interface Props {
  children: React.ReactNode;
  theme?: string;
}

const WrappedComponent = ({ children, theme = "dark" }: Props) => (
  <div className={`app-container ${theme}`}>{children}</div>
);

const enhance = compose<{}, Props>(
  withContext(
    {
      store: PropTypes.object
    },
    () => ({
      store: createStore()
    })
  ),
  DragDropContext(HTML5Backend),
  FontAwesomeProvider,
  KeyIsDown()
  // connect( TODO
  //   ({ userSettings: { theme } }) => ({
  //     theme
  //   }),
  //   {}
  // )
);

const Wrapper = enhance(WrappedComponent);

export default Wrapper;
