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

import { storiesOf, addDecorator } from '@storybook/react';

import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';

import 'semantic/dist/semantic.min.css';

import { testXslt } from './test';

import XsltEditor from './XsltEditor';

const PollyDecoratorWithTestData = PollyDecorator({
  xslt: testXslt,
});

const stories = storiesOf('XSLT Editor', module)
  .addDecorator(PollyDecoratorWithTestData)
  .addDecorator(ReduxDecorator);

Object.entries(testXslt)
  .map(k => ({
    name: k[0],
    data: k[1],
  }))
  .forEach(xslt => stories.add(xslt.name, () => <XsltEditor xsltId={xslt.name} />));
