import React from 'react';

import { storiesOf } from '@storybook/react';

import DocRefImage from './DocRefImage';

storiesOf('Doc Ref Image', module)
  .add('default (large)', () => <DocRefImage docRefType="XSLT" />)
  .add('small', () => <DocRefImage size="small" docRefType="Feed" />)
  .add('large', () => <DocRefImage size="large" docRefType="Pipeline" />);
