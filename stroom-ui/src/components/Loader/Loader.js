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
import PropTypes from 'prop-types';
import { default as ReactLoader } from 'react-loader';

var options = {
  lines: 13,
  length: 20,
  width: 10,
  radius: 30,
  scale: 0.50,
  corners: 1,
  color: '#000',
  opacity: 0.25,
  rotate: 0,
  direction: 1,
  speed: 1,
  trail: 60,
  fps: 20,
  zIndex: 2e9,
  top: '50%',
  left: '50%',
  shadow: false,
  hwaccel: false,
  position: 'relative'
};

/**
 * Configures and wraps react-loader, which itself wraps spin.js. Isn't 2018 great? 
 * Adds a message.
 */
const Loader = ({ message }) => (
  <div className="loader__container">
    <ReactLoader options={options} />
    <p>{message}</p>
  </div>
);


Loader.propTypes = {
  message: PropTypes.string,
};

export default Loader;