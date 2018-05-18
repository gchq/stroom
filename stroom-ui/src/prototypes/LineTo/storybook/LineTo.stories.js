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
import PropTypes from 'prop-types';

import { storiesOf } from '@storybook/react';

import { LineContainer, LineTo } from '../index';

import {
    domRectBoundCalcs
} from '../LineTo';


let testBlockStyle = {
    position: 'fixed',
    width:'50px',
    backgroundColor: 'red',
    borderStyle: 'thin',
    color: 'white'
}

storiesOf('Line To', module)
    .add('Single Line', () => {
        return (
            <div>
                <LineContainer id='testLines1'>
                    <div id='myFirst' style={{
                        ...testBlockStyle,
                        top: '50px', 
                        left: '50px'}}>From</div>
                    <div id='mySecond' style={{
                        ...testBlockStyle,
                        top: '250px', 
                        left: '150px'}}>To</div>
                    <LineTo fromId='myFirst' toId='mySecond' />
                </LineContainer>
            </div>
        )
    })
    .add('Customised Line', () => {
        return (
            <div>
                <LineContainer id='testLines2'>
                    <div id='myFirst' style={{
                        ...testBlockStyle,
                        top: '50px', 
                        left: '150px'}}>From</div>
                    <div id='mySecond' style={{
                        ...testBlockStyle,
                        top: '250px', 
                        left: '50px'}}>To</div>
                    <LineTo 
                        fromId='myFirst' 
                        toId='mySecond' 
                        calculateStart={domRectBoundCalcs.leftCentre}
                        calculateEnd={domRectBoundCalcs.rightCentre}/>
                </LineContainer>
            </div>
        )
    })