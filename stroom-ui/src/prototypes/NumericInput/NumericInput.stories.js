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

import React from 'react'
import { storiesOf } from '@storybook/react'
import NumericInput from './NumericInput'

import './NumericInput.css'

storiesOf('NumericInput', module)
  .add('basic', () => (
    <div className='container'>
      <NumericInput />
    </div>
  ))
  .add('with default value', () => (
    <div className='container'>
      <NumericInput defaultValue={10} />
    </div>
  ))
  .add('with placeholder value', () => (
    <div className='container'>
      <NumericInput placeholder={11} />
    </div>
  ))
  .add('with max value of 4', () => (
    <div className='container'>
      <NumericInput max={4} value={2} />
    </div>
  ))
  .add('with min value of -1', () => (
    <div className='container'>
      <NumericInput min={-1} value={0} />
    </div>
  ))
  .add('with min value of 42 and a max value of 46', () => (
    <div className='container'>
      <NumericInput min={42} max={46} value={44} />
    </div>
  ))
  .add('with value lower than min', () => (
    <div className='container'>
      <NumericInput min={4} value={2} />
    </div>
  ))
  .add('with value high than max', () => (
    <div className='container'>
      <NumericInput max={4} value={6} />
    </div>
  ))
