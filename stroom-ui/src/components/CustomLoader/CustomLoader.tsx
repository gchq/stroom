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

interface Props {
  title: string;
  message: string;
}

/**
 * Configures and wraps react-loader, which itself wraps spin.js. Isn't 2018 great?
 * Adds a message.
 */
const CustomLoader: React.FunctionComponent<Props> = ({ title, message }) => (
  <div id="loading">
    <div id="loadingBox">
      <div id="loadingLeft">
        <div className="spinner-blue" role="status">
          <span className="sr-only">Loading...</span>
        </div>
      </div>
      <div id="loadingRight">
        <div id="loadingTitle">{title}</div>
        <div id="loadingText">{message}</div>
      </div>
    </div>
  </div>
);

export default CustomLoader;
