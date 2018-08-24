import { connect } from 'react-redux';
import { lifecycle, compose, withProps } from 'recompose';
import Mousetrap from 'mousetrap';
import { withRouter } from 'react-router-dom';

import { actionCreators } from './redux';
import { SHORTCUT_NAMES, handleShortcutKey } from './handleShortcutKey';

const { keyDown, keyUp } = actionCreators;

const upKeys = ['k', 'ctrl+k', 'up'];
const downKeys = ['j', 'ctrl+j', 'down'];
const openKeys = ['enter'];

// We need to prevent up and down keys from moving the cursor around in the input

// I'd rather use Mousetrap for these shortcut keys. Historically Mousetrap
// hasn't handled keypresses that occured inside inputs or textareas.
// There were some changes to fix this, like binding specifically
// to a field. But that requires getting the element from the DOM and
// we'd rather not break outside React to do this. The other alternative
// is adding 'mousetrap' as a class to the input, but that doesn't seem to work.

// Up
const upArrow = 38;
const k = 75;

// Down
const downArrow = 40;
const j = 74;

const enter = 13;

const withInputKeyDown = compose(
  withRouter,
  connect(({ keyIsDown: { focussedElement } }, props) => ({ focussedElement }), {
    handleShortcutKey,
  }),
  withProps(({ focussedElement, handleShortcutKey, history }) => ({
    onInputKeyDown: (e) => {
      if (focussedElement) {
        if (e.keyCode === upArrow || (e.ctrlKey && e.keyCode === k)) {
          handleShortcutKey(history, SHORTCUT_NAMES.UP);
          e.preventDefault();
        } else if (e.keyCode === downArrow || (e.ctrlKey && e.keyCode === j)) {
          handleShortcutKey(history, SHORTCUT_NAMES.DOWN);
          e.preventDefault();
        } else if (e.keyCode === enter) {
          handleShortcutKey(history, SHORTCUT_NAMES.OPEN);
          e.preventDefault();
        }
      }
    },
  })),
);

const KeyIsDown = (filters = ['Control', 'Shift', 'Alt', 'Meta']) =>
  compose(
    withRouter,
    connect(({ keyIsDown: { focussedElement } }, props) => ({ focussedElement }), {
      keyDown,
      keyUp,
      handleShortcutKey,
    }),
    lifecycle({
      componentDidMount() {
        const {
          keyDown, keyUp, handleShortcutKey, focussedElement, history,
        } = this.props;
        window.onkeydown = function (e) {
          if (focussedElement && filters.includes(e.key)) {
            keyDown(e.key);
            e.preventDefault();
          }
        };
        window.onkeyup = function (e) {
          if (focussedElement && filters.includes(e.key)) {
            keyUp(e.key);
            e.preventDefault();
          }
        };

        Mousetrap.bind(upKeys, () => {
          handleShortcutKey(history, SHORTCUT_NAMES.UP);
        });
        Mousetrap.bind(downKeys, () => {
          handleShortcutKey(history, SHORTCUT_NAMES.DOWN);
        });
        Mousetrap.bind(openKeys, () => {
          handleShortcutKey(history, SHORTCUT_NAMES.OPEN);
        });
      },
      componentWillUnmount() {
        Mousetrap.unbind(upKeys);
        Mousetrap.unbind(downKeys);
        Mousetrap.unbind(openKeys);
      },
    }),
  );

export default KeyIsDown;

export { withInputKeyDown };
