import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { storiesOf, addDecorator } from '@storybook/react';
import { action } from '@storybook/addon-actions';
import { withNotes } from '@storybook/addon-notes';

import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import {
    PipelineEditor
} from '../index'


storiesOf('Pipeline Editor', module)
    .addDecorator(ReduxDecorator) // must be recorder after/outside of the test initialisation decorators
    .addDecorator(DragDropDecorator)
    .add('Pipeline Editor (empty)', () => 
        <PipelineEditor 
            pipelineId='empty-pipe'
            />
    )