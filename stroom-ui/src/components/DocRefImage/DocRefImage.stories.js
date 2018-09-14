import React from 'react';

import { storiesOf } from '@storybook/react';

import DocRefImage from './DocRefImage';

storiesOf('Doc Ref Image', module)
  .add('default (large)', () => <DocRefImage docRefType="XSLT" />)
  .add('small', () => <DocRefImage size="sm" docRefType="Feed" />)
  .add('large', () => <DocRefImage size="lg" docRefType="Pipeline" />);
