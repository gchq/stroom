import React from 'react';
import { Provider as ReduxProvider } from 'react-redux';
import { browserHistory } from 'react-router';

import store from 'startup/store';

export default function Provider({ story }) {
  return <ReduxProvider store={store}>{story}</ReduxProvider>;
}
