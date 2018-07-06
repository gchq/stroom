import {
  actionCreators as explorerTreeActionCreators,
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

const actionCreators = {
  ...explorerTreeActionCreators,
  ...permissionInheritancePickerActionCreators,
  ...docRefPickerActionCreators,
  ...moveDocRefActionCreators,
};

export {
  actionCreators,
  explorerTreeReducer,
  permissionInheritancePickerReducer,
  docRefPickerReducer,
  moveDocRefReducer,
};
