import { combineReducers } from 'redux';

import {
  actionCreators as docExplorerActionCreators,
  reducer as explorerTreeReducer,
} from './explorerTreeReducer';
import {
  actionCreators as permissionInheritancePickerActionCreators,
  reducer as permissionInheritancePickerReducer,
} from './permissionInheritancePickerReducer';
import {
  actionCreators as docRefPickerActionCreators,
  reducer as docRefPickerReducer,
} from './docRefPickerReducer';
import {
  actionCreators as moveDocRefActionCreators,
  reducer as moveDocRefReducer,
} from './moveDocRefReducer';
import {
  actionCreators as renameDocRefActionCreators,
  reducer as renameDocRefReducer,
} from './renameDocRefReducer';
import {
  actionCreators as deleteDocRefActionCreators,
  reducer as deleteDocRefReducer,
} from './renameDocRefReducer';
import {
  actionCreators as copyDocRefActionCreators,
  reducer as copyDocRefReducer,
} from './copyDocRefReducer';

const actionCreators = {
  ...docExplorerActionCreators,
  ...permissionInheritancePickerActionCreators,
  ...docRefPickerActionCreators,
  ...moveDocRefActionCreators,
  ...renameDocRefActionCreators,
  ...deleteDocRefActionCreators,
  ...copyDocRefActionCreators,
};

const reducer = combineReducers({
  moveDocRef: moveDocRefReducer,
  explorerTree : explorerTreeReducer,
  permissionInheritancePicker: permissionInheritancePickerReducer,
  docRefPicker: docRefPickerReducer,
  moveDocRef: moveDocRefReducer,
  renameDocRef: renameDocRefReducer,
  deleteDocRef: deleteDocRefReducer,
  copyDocRef: copyDocRefReducer
})

export {
  actionCreators,
  reducer
};
