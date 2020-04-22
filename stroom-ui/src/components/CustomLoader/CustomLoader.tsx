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
      <div id="loadingImage" className="mdl-spinner mdl-spinner--single-color mdl-js-spinner is-active is-upgraded"
           data-upgraded=",MaterialSpinner">
        <div className="mdl-spinner__layer mdl-spinner__layer-1">
          <div className="mdl-spinner__circle-clipper mdl-spinner__left">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__gap-patch">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__circle-clipper mdl-spinner__right">
            <div className="mdl-spinner__circle"></div>
          </div>
        </div>
        <div className="mdl-spinner__layer mdl-spinner__layer-2">
          <div className="mdl-spinner__circle-clipper mdl-spinner__left">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__gap-patch">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__circle-clipper mdl-spinner__right">
            <div className="mdl-spinner__circle"></div>
          </div>
        </div>
        <div className="mdl-spinner__layer mdl-spinner__layer-3">
          <div className="mdl-spinner__circle-clipper mdl-spinner__left">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__gap-patch">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__circle-clipper mdl-spinner__right">
            <div className="mdl-spinner__circle"></div>
          </div>
        </div>
        <div className="mdl-spinner__layer mdl-spinner__layer-4">
          <div className="mdl-spinner__circle-clipper mdl-spinner__left">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__gap-patch">
            <div className="mdl-spinner__circle"></div>
          </div>
          <div className="mdl-spinner__circle-clipper mdl-spinner__right">
            <div className="mdl-spinner__circle"></div>
          </div>
        </div>
      </div>
      <div id="loadingTitle">{title}</div>
      <div id="loadingText">{message}</div>
    </div>
  </div>
);

export default CustomLoader;
