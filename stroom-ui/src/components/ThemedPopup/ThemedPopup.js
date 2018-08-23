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
import { compose } from 'recompose';
import { connect } from 'react-redux';
import ReactTooltip from 'react-tooltip';

const enhance = compose(connect(
  ({ userSettings: { theme } }) => ({
    theme,
  }),
  {},
));

/**
 * A themed popup is required because Semantic UI popups are mounted
 * outside the application's root div. This means it won't inherit the
 * 'theme-dark' or 'theme-light' class name. We can add it here easily
 * enough.
 */
const ThemedPopup = ({
  trigger, content, theme, ...rest
}) => (
  <div className={theme}>
    <a data-tip={content}> {trigger} </a>
    <ReactTooltip className="tooltip-popup" effect="solid" />
  </div>
);
export default enhance(ThemedPopup);
