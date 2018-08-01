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
import { connect } from 'react-redux';
import { compose, lifecycle, branch, renderComponent } from 'recompose';
import { Loader, Icon, Popup } from 'semantic-ui-react';
import { path, splitAt } from 'ramda';

import AceEditor from 'react-ace';

// eslint-disable-next-line
import brace from 'brace';
import 'brace/mode/xml';
import 'brace/theme/github';
import 'brace/keybinding/vim';

const EventView = ({ events }) => (
  <div className="EventView__container">
    <div className="EventView__aceEditor__container">
      <AceEditor
        style={{ width: '100%', height: '100%', minHeight: '25rem' }}
        mode="xml"
        theme="github"
        keyboardHandler="vim"
        value={events}
        readOnly
      />
    </div>
  </div>
);

EventView.propTypes = {
  events: PropTypes.array.isRequired,
};

export default EventView;
