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

import React from 'react';

import IconHeader from 'components/IconHeader';

const Welcome = props => (
  <div className="welcome">
    <IconHeader icon="home" text="Welcome" />
    <div className="welcome__shortcuts">
      <h4>Global shortcut keys</h4>
      <table className="welcome__shortcuts__table">
        <tr>
          <th />
          <th>Shortcut</th>
        </tr>
        <tr>
          <td>Document search</td>
          <td><code>ctrl + shift + f</code></td>
        </tr>
        <tr>
          <td> Recent documents </td>
          <td><code> ctrl + shift + e</code></td>
        </tr>
      </table>
    </div>
  </div>
);

export default Welcome;
