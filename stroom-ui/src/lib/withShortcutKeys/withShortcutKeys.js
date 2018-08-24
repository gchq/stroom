import { compose, lifecycle, withProps } from 'recompose';
import Mousetrap from 'mousetrap';

class ShortcutAction {
  constructor(parent) {
    this.action = () => {
      console.log('No Action Taken');
    };
    this.keyEventMatcher = () => false;
    this.mousetrapKeys = undefined;
    this.parent = parent;
  }

  withAction(action) {
    this.action = action;
    return this;
  }

  withMousetrapKeys(k) {
    this.mousetrapKeys = k;
    return this;
  }

  withKeyEventMatcher(keyEventMatcher) {
    this.keyEventMatcher = keyEventMatcher;
    return this;
  }

  bindMousetrap(getProps) {
    if (this.mousetrapKeys) {
      Mousetrap.bind(this.mousetrapKeys, () => this.action(getProps()));
    }
  }

  unbindMousetrap() {
    if (this.mousetrapKeys) {
      Mousetrap.unbind(this.mousetrapKeys);
    }
  }

  fireActionIfMatch(getProps, e) {
    if (this.keyEventMatcher(e)) {
      this.action(getProps(), e);
    }
  }

  endShortcutAction() {
    const p = this.parent;
    this.parent = undefined;
    return p;
  }
}

class ShortcutActionCollection {
  constructor() {
    this.shortcutActions = [];
    this.element = undefined;
  }

  beginShortcutAction() {
    const sa = new ShortcutAction(this);
    this.shortcutActions.push(sa);
    return sa;
  }

  // This function generates the actual HOC, using the shortcut information gathered thus far.
  // It provides various functions that the wrapped component can use to ensure the lifecycle is correctly managed.
  build() {
    // We will need to take a record of the element, so we can fetch it's props in our event handlers.
    // If we do not do this, then the closure takes the props at the point of 'onFocus' being called.
    const that = this;

    return compose(
      lifecycle({
        componentWillMount() {
          that.element = this; // take a pointer to the React element.
        },
      }),
      withProps(props => ({
        enableShortcuts: () => {
          this.shortcutActions.forEach(sa => sa.bindMousetrap(() => that.element.props));
        },
        disableShortcuts: () => {
          this.shortcutActions.forEach(sa => sa.unbindMousetrap());
        },
        onKeyDownWithShortcuts: (e) => {
          this.shortcutActions.forEach(sa => sa.fireActionIfMatch(() => that.element.props, e));
        },
      })),
    );
  }
}

const withShortcutKeys = () => new ShortcutActionCollection();

export default withShortcutKeys;
