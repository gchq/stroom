import { compose, lifecycle, withProps } from 'recompose';
import Mousetrap from 'mousetrap';

/**
 * Need to wrap the component in an instance so this I can put the element to one side
 * within the lifecycle, then it can be access in the withProps.
 * This is due to the closure scope of the anonymous functions created for the key bindings.
 */
class ShortcutKeyComponent {
  constructor(shortcutActions) {
    this.element = undefined;
    this.shortcutActions = shortcutActions;
    console.log('Creating Shortcut Key Component Thing');
    // IT IS ONLY CREATING ONE INSTANCE OF THIS PER COMPONENT CLAASS
  }

  build() {
    const enableShortcuts = function () {
      console.log('Enable Shortcuts', this.element.props.menuItem);
      this.shortcutActions.forEach((sa) => {
        if (sa.mousetrapKeys && sa.action) {
          Mousetrap.bind(sa.mousetrapKeys, () => sa.action(this.element.props));
        }
      });
    }.bind(this);

    const disableShortcuts = function () {
      console.log('Disable Shortcuts', this.element.props.menuItem);
      this.shortcutActions.forEach((sa) => {
        if (sa.mousetrapKeys) {
          Mousetrap.unbind(sa.mousetrapKeys);
        }
      });
    }.bind(this);

    const onKeyDownWithShortcuts = function (e) {
      this.shortcutActions.forEach((sa) => {
        if (sa.action && sa.keyEventMatcher && sa.keyEventMatcher(e)) {
          sa.action(this.element.props, e);
        }
      });
    }.bind(this);

    const that = this;
    return compose(
      lifecycle({
        componentWillMount() {
          that.element = this; // take a pointer to the React element.
          console.log('Mounting a Shortcut Component', that.element.props.menuItem);
        },
      }),
      withProps(props => ({
        enableShortcuts,
        disableShortcuts,
        onKeyDownWithShortcuts,
      })),
    );
  }
}

const withShortcutKeys = shortcutActions => new ShortcutKeyComponent(shortcutActions).build();

export default withShortcutKeys;
