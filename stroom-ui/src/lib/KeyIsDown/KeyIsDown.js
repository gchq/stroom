import { connect } from 'react-redux';
import { lifecycle, compose, withProps } from 'recompose';
import Mousetrap from 'mousetrap';

import { actionCreators } from './redux';
import { SHORTCUT_NAMES, shortcutUsed } from './shortcutUsed';

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
  connect(({ keyIsDown: { focussedElement } }, props) => ({ focussedElement }), {
    shortcutUsed,
  }),
  withProps(({ focussedElement, shortcutUsed }) => ({
    onInputKeyDown: (e) => {
      if (focussedElement) {
        if (e.keyCode === upArrow || (e.ctrlKey && e.keyCode === k)) {
          shortcutUsed(SHORTCUT_NAMES.UP);
          e.preventDefault();
        } else if (e.keyCode === downArrow || (e.ctrlKey && e.keyCode === j)) {
          shortcutUsed(SHORTCUT_NAMES.DOWN);
          e.preventDefault();
        } else if (e.keyCode === enter) {
          shortcutUsed(SHORTCUT_NAMES.OPEN);
          e.preventDefault();
        }
      }
    },
  })),
);

const KeyIsDown = (filters = ['Control', 'Shift', 'Alt', 'Meta']) =>
  compose(
    connect(({ keyIsDown: { keyIsDown } }, props) => ({ keyIsDown }), {
      keyDown,
      keyUp,
      shortcutUsed,
    }),
    lifecycle({
      componentDidMount() {
        const {
          keyIsDown, keyDown, keyUp, shortcutUsed,
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
          shortcutUsed(SHORTCUT_NAMES.UP);
        });
        Mousetrap.bind(downKeys, () => {
          shortcutUsed(SHORTCUT_NAMES.DOWN);
        });
        Mousetrap.bind(openKeys, () => {
          shortcutUsed(SHORTCUT_NAMES.OPEN);
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
