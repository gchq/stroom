import { createFactory, Component } from 'react';
import { setDisplayName, wrapDisplayName } from 'recompose';

/**
 * Works like withState but stores the value in localStorage, using React state as a cache.
 *
 * Unlike withState this does not support updater functions.
 *
 * @param {string} stateName
 * @param {string} stateUpdaterName
 * @param {string|boolean} noLocalStorageInitialState
 */
const withLocalStorage = (
  stateName,
  stateUpdaterName,
  noLocalStorageInitialState,
) => (BaseComponent) => {
  const factory = createFactory(BaseComponent);
  class WithLocalStorage extends Component {
    /**
     * localStorage uses strings, so we need to make sure that if we've stored a boolean it
     * gets correctly converted back to one.
     */
    getValue = (stateName, noLocalStorageInitialState) => {
      const rawValue = localStorage.getItem(stateName);
      if (rawValue) {
        // localStorage uses strings, so we need to make sure that if we've
        // stored a boolean it gets correctly converted back to one.
        return rawValue === 'true' ? true : rawValue === 'false' ? false : rawValue;
      }
      return noLocalStorageInitialState;
    };

    state = {
      localStorageValue: this.getValue(stateName, noLocalStorageInitialState),
    };

    updateLocalStorageValue = (valueToSet) => {
      localStorage.setItem(stateName, valueToSet);
      this.setState(({ localStorageValue }) => ({
        localStorageValue: valueToSet,
      }));
    };

    render() {
      return factory({
        ...this.props,
        [stateName]: this.state.localStorageValue,
        [stateUpdaterName]: this.updateLocalStorageValue,
      });
    }
  }

  if (process.env.NODE_ENV !== 'production') {
    return setDisplayName(wrapDisplayName(BaseComponent, 'withLocalStorage'))(WithLocalStorage);
  }
  return WithLocalStorage;
};

export default withLocalStorage;
