import {
  actionCreators as docExplorerActionCreators,
  reducer as docExplorerReducer,
} from './docExplorerReducer';
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
  ...docExplorerActionCreators,
  ...permissionInheritancePickerActionCreators,
  ...docRefPickerActionCreators,
  ...moveDocRefActionCreators,
};

export {
  actionCreators,
  docExplorerReducer,
  permissionInheritancePickerReducer,
  docRefPickerReducer,
  moveDocRefReducer,
};
