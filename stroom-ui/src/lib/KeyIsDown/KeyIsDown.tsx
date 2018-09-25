import { connect } from "react-redux";
import { lifecycle, compose } from "recompose";

import { actionCreators } from "./redux";

const { keyDown, keyUp } = actionCreators;

export interface Props {
  keyDown: (key: string) => void;
  keyUp: (key: string) => void;
}

const KeyIsDown = (
  filters: Array<String> = ["Control", "Shift", "Alt", "Meta"]
) =>
  compose(
    connect(
      undefined,
      {
        keyDown,
        keyUp
      }
    ),
    lifecycle<Props, {}, {}>({
      componentDidMount() {
        const { keyDown, keyUp } = this.props;
        window.onkeydown = function(e) {
          if (filters.indexOf(e.key) !== -1) {
            keyDown(e.key);
            e.preventDefault();
          }
        };
        window.onkeyup = function(e) {
          if (filters.indexOf(e.key) !== -1) {
            keyUp(e.key);
            e.preventDefault();
          }
        };
      }
    })
  );

export default KeyIsDown;
