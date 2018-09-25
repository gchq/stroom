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

import { lifecycle } from 'recompose';
import { library } from '@fortawesome/fontawesome-svg-core';

import {
  faFolder,
  faAngleRight,
  faAngleUp,
  faAngleDown,
  faArrowLeft,
  faSearch,
  faHistory,
  faExclamationCircle,
  faExclamationTriangle,
  faBars,
  faCircle,
  faTrash,
  faTasks,
  faUsers,
  faUser,
  faHome,
  faKey,
  faCaretDown,
  faCaretRight,
  faInfoCircle,
  faInfo,
  faBomb,
  faFile,
  faDatabase,
  faPlay,
  faQuestionCircle,
  faPlus,
  faTimes,
  faCogs,
  faCog,
  faQuestion,
  faRecycle,
  faBan,
  faCheck,
  faCopy,
  faEdit,
  faArrowsAlt,
  faICursor,
  faSave,
  faChevronLeft,
  faChevronRight,
} from '@fortawesome/free-solid-svg-icons';

export default lifecycle({
  componentWillMount() {
    library.add(
      faFolder,
      faAngleRight,
      faAngleUp,
      faAngleDown,
      faArrowLeft,
      faSearch,
      faHistory,
      faExclamationCircle,
      faExclamationTriangle,
      faBars,
      faPlus,
      faCircle,
      faTimes,
      faCogs,
      faCog,
      faTrash,
      faTasks,
      faUsers,
      faUser,
      faHome,
      faKey,
      faCaretDown,
      faCaretRight,
      faInfoCircle,
      faInfo,
      faBomb,
      faFile,
      faDatabase,
      faPlay,
      faQuestionCircle,
      faQuestion,
      faRecycle,
      faBan,
      faCheck,
      faCopy,
      faEdit,
      faArrowsAlt,
      faICursor,
      faSave,
      faChevronLeft,
      faChevronRight,
    );
  },
});
