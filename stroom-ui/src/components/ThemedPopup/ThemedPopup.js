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
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import ReactTooltip from 'react-tooltip';
import uuidv4 from 'uuid/v4';

const enhance = compose(
  connect(
    ({ userSettings: { theme } }) => ({
      theme,
    }),
    {},
  ),
  // It'd be nice to use the simpler form of tooltip, the one where
  // the content is in the anchor tag. But this only works for simple
  // content and we want to support richer content, e.g. for
  // multi-line tooltips. If we're going to do it this way then we need
  // an ID to tie the anchor tag to the component. We don't have one
  // so to prevent conflicts we'll pass in a UUID.
  withProps(() => ({ uuid: uuidv4() })),
);

/**
 * A themed popup is required because Semantic UI popups are mounted
 * outside the application's root div. This means it won't inherit the
 * 'theme-dark' or 'theme-light' class name. We can add it here easily
 * enough.
 */
const ThemedPopup = ({
  trigger, content, theme, uuid, ...rest
}) => (
  <div className={theme}>
    <a data-tip data-for={uuid}>
      {trigger}
    </a>
    <ReactTooltip id={uuid} className="tooltip-popup raised-low" effect="solid">
      {content}
    </ReactTooltip>
  </div>
);

export default enhance(ThemedPopup);
