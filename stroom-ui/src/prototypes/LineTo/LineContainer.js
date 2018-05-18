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

import LineContext from './LineContext';

const LineContainer = (props) => (
    <LineContext.Provider value={props.id}>
        <div>
            <canvas 
                id={props.id} 
                width='1000px'
                height='1000px'
                style={{
                    'position':'absolute',
                    'top':0,
                    'left':0
                }}/>
            {props.children}
        </div>
    </LineContext.Provider>
);

LineContainer.propTypes = {
    id : PropTypes.string.isRequired
}

export default LineContainer;