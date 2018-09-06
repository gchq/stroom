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
import PropTypes from 'prop-types';
// eslint-disable-next-line
import brace from 'brace';
import 'brace/mode/xml';
import 'brace/theme/github';
import 'brace/theme/ambiance';
import 'brace/keybinding/vim';

import ThemedAceEditor from 'components/ThemedAceEditor';

const EventView = ({ events }) => (
  <div className="EventView__container">
    <div className="EventView__aceEditor__container">
      <ThemedAceEditor
        className="EventView__aceEditor"
        style={{ width: '100%', height: '100%' }}
        mode="xml"
        value={events}
        readOnly
      />
    </div>
  </div>
);

EventView.propTypes = {
  events: PropTypes.string.isRequired,
};

export default EventView;
