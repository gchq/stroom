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

const actionCreators = {
  ...explorerTreeActionCreators,
  ...permissionInheritancePickerActionCreators,
  ...docRefPickerActionCreators,
};

export {
  actionCreators,
  explorerTreeReducer,
  permissionInheritancePickerReducer,
  docRefPickerReducer,
};
