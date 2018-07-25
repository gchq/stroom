/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import DocExplorer from './DocExplorer';
import DocRefInfoModal from './DocRefInfoModal';
import DocPicker, { DocPickerModal } from './DocPicker/index';
import DocRefTypePicker from './DocRefTypePicker';
import PermissionInheritancePicker from './PermissionInheritancePicker';
import permissionInheritanceValues from './permissionInheritanceValues';
import withExplorerTree from './withExplorerTree';
import withDocRefTypes from './withDocRefTypes';
import explorerClient from './explorerClient';

export {
  DocExplorer,
  DocRefInfoModal,
  DocPickerModal,
  DocPicker,
  DocRefTypePicker,
  PermissionInheritancePicker,
  permissionInheritanceValues,
  withExplorerTree,
  withDocRefTypes,
  explorerClient
};

export default DocExplorer;
