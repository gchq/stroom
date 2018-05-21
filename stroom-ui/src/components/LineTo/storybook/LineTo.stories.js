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

import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';

import { LineContainer, LineTo } from '../index';

let testBlockStyle = {
    position: 'absolute',
    width:'50px',
    backgroundColor: 'red',
    borderStyle: 'thin',
    color: 'white'
}

storiesOf('Line To SVG', module)
    .addDecorator(ReduxDecorator)
    .add('Single Line', () => {
        return (
            <div>
                <LineContainer lineContextId='testLines1'>
                    <div id='myFirst' style={{
                        ...testBlockStyle,
                        top: '50px', 
                        left: '50px'}}>From</div>
                    <div id='mySecond' style={{
                        ...testBlockStyle,
                        top: '250px', 
                        left: '150px'}}>To</div>
                    <LineTo 
                        lineId='myLine1'
                        fromId='myFirst'
                        toId='mySecond' 
                        />
                </LineContainer>
            </div>
        )
    })
    .add('Custom Curve', () => {
        const generateCurve = ({lineId, fromRect, toRect}) => {
            let from = {
                x : fromRect.left + (fromRect.width / 2),
                y : fromRect.bottom
            };
            let to = {
                x : toRect.left,
                y : toRect.top + (toRect.height / 2)
            };
            let pathSpec = 'M ' + from.x + ' ' + from.y
                        + ' C ' + from.x + ' ' + from.y + ' '
                                + from.x + ' ' + to.y + ' '
                                + to.x + ' ' + to.y;
            return (
                <path key={lineId}  d={pathSpec} style={{
                    stroke:'black',
                    strokeWidth: 2,
                    fill: 'none'
                }} />
            )
        }

        return (
            <div>
                <LineContainer lineContextId='testLines2' lineElementCreator={generateCurve}>
                    <div id='myFirst' style={{
                        ...testBlockStyle,
                        top: '50px', 
                        left: '150px'}}>From</div>
                    <div id='mySecond' style={{
                        ...testBlockStyle,
                        top: '250px', 
                        left: '50px'}}>Mid1</div>
                    <div id='myThird' style={{
                        ...testBlockStyle,
                        top: '150px', 
                        left: '350px'}}>End</div>
                    <LineTo 
                        lineId='myLine2'
                        fromId='myFirst'
                        toId='mySecond' 
                        />
                    <LineTo 
                        lineId='myLine3'
                        fromId='mySecond'
                        toId='myThird' 
                        />
                </LineContainer>
            </div>
        )
    })