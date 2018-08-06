import { combineReducers } from 'redux';

import {
  reducer as menuItemsOpenReducer,
  actionCreators as menuItemsOpenActionCreators,
} from './menuItemsOpenReducer';

const actionCreators = {
  ...menuItemsOpenActionCreators,
};

const reducer = combineReducers({
  menuItemsOpen: menuItemsOpenReducer,
});

export { actionCreators, reducer };
