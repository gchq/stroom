import React from 'react';

import { storiesOf } from '@storybook/react';

import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';

import Basic from './Basic';

storiesOf('Basic', module).add('Basic', () => <Basic />);
