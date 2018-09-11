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

const styles = {
  biglyStyles: {
    width: '100%',
    height: '100%'
  },
  containerStyles: {
    width: '100%',
    height: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center'
  },
}

const themes = ['theme-light', 'theme-dark'];

const ThemedContainer = ({ theme, component }) => (
  <div className={`app-container ${theme} raised-low`} style={styles.biglyStyles}>
    <div className='flat' style={styles.containerStyles}>
      {component}
    </div>
  </div>
);

export const addThemedStories = (stories, component) => {
  themes.forEach(theme => stories.add(theme, () => <ThemedContainer theme={theme} component={component} />));
}
