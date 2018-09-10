import React, { Component } from 'react';
import { connect } from 'react-redux';

const enhanceLocal = connect(
  ({ userSettings: { theme } }) => ({
    theme,
  }),
  {},
);

class WrappedComponent extends Component {
  render() {
    return <div className={`app-container ${this.props.theme}`}>{this.props.children}</div>;
  }
}

const ThemedComponent = enhanceLocal(WrappedComponent);

export const ThemedDecorator = storyFn => <ThemedComponent>{storyFn()}</ThemedComponent>;
