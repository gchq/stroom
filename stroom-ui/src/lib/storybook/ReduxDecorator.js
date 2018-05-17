import React from 'react';
import { Provider } from 'react-redux';

import store from 'startup/store';

export const ReduxDecorator = (storyFn) => (
  <Provider store={store}>
      {storyFn()}
  </Provider>
)