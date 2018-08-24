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
import AppChrome from './AppChrome';
import appChromeRoutes from './appChromeRoutes';

// NOTE: DO NOT PUBLISH 'redux' STUFF FROM HERE, YOU END WITH HORRID CIRCULAR DEPENDENCIES
// Other parts of the app use the redux actions to open tabs
// The Tab displaying component is dependant on everything else because it displays all other components
// You get some nasty undefined crashes if you export redux stuff from here and import from here

export { AppChrome, appChromeRoutes };

export default AppChrome;
