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

import React, { Component } from 'react'

import { Link } from 'react-router-dom'

import './Home.css'

class Home extends Component {
  render () {
    return (
      <div className='home-container'>
        <h1>Welcome to Stroom UI components</h1>

        <h2>Sections</h2>
        <ul>
          <li>
            <Link to='/trackers'>Tracker dashboard</Link>
          </li>
        </ul>

        <h2>Prototypes</h2>
        <ul>
          <li>
            <Link to='/prototypes/original_list'>Original List</Link>
          </li>
          <li>
            <Link to='/prototypes/graph'>Graph</Link>
          </li>
        </ul>

        <h2>Storybook</h2>
        <p>If Storybook is running you can go there to look at the components (which should include the prototypes)</p>
        <ul>
          <li>
            <a href='http://localhost:9001'>Storybook</a>
          </li>
        </ul>

      </div>
    )
  }
}

export default Home
