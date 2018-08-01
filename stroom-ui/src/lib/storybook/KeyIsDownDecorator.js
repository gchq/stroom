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
import React, { Component } from 'react';

import KeyIsDown from 'prototypes/KeyIsDown';

class WrappedComponent extends Component {
  render() {
    return this.props.children;
  }
}

export const KeyIsDownDecorator = filters => (storyFn) => {
  const KeyDownComponent = KeyIsDown(filters)(WrappedComponent);
  return <KeyDownComponent>{storyFn()}</KeyDownComponent>;
};
