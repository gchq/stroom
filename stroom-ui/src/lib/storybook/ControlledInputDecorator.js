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
import { withState } from 'recompose';

const withValue = withState('value', 'onChange', undefined);

class RawControlledInput extends React.PureComponent {
  render() {
    const { children, onChange, value } = this.props;
    
    const childrenWithProps = React.Children.map(children, child =>
      React.cloneElement(child, { onChange, value }));

    return <div>{childrenWithProps}</div>;
  }
}

export const ControlledInput = withValue(RawControlledInput);

export const ControlledInputDecorator = storyFn => <ControlledInput>{storyFn()}</ControlledInput>;
