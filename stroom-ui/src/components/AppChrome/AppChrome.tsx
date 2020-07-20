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
import * as React from "react";

import "simplebar";

import { ActiveMenuItem } from "./types";
import { useIncludeSidebar } from "lib/useRouter/BrowserRouter";
import { Sidebar } from "./Sidebar";

export interface AppChromeProps {
  content: React.ReactNode;
  urlPrefix: string;
  activeMenuItem: ActiveMenuItem;
}

const AppChrome: React.FunctionComponent<AppChromeProps> = ({
  activeMenuItem,
  urlPrefix,
  content,
}) => {
  const includeSidebar = useIncludeSidebar(urlPrefix);

  return (
    <div className="app-chrome page">
      {includeSidebar && <Sidebar {...{ activeMenuItem }} />}
      <div className="app-chrome__content">
        <div className="content-tabs">
          <div className="content-tabs__content">{content}</div>
        </div>
      </div>
    </div>
  );
};

export default AppChrome;
