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
import ReactTable from 'react-table';
import 'react-table/react-table.css';

class ReactTableWrapper extends Component {

  constructor(props) {
    super(props)

    this.state = {
      height: 0
    }
  }

  componentDidMount() {
    const height = this.wrapper.clientHeight;
    this.setState({ height });
  }

  render() {
    const { tableColumns, tableData } = this.props;
    console.log('hullo')
    return (
      <div
        className="react-table-wrapper"
        ref={(wrapper) => this.wrapper = wrapper}
      > <p>{this.state.height}</p>



        AFTER HOLS:
        THIS DOESN'T WORK BECAUSE THE HEIGHT IS ALREADY SET!
        WE NEED THE HEIGHT BEFORE THE TABLE RENDERS AND MAKES THE DIV MASSIVE.
        MAYBE A DELAY IN RENDERING IT, FOLLOWED BY A PROP UPDATE?
  
  
  
  
  
        <ReactTable
          manual
          sortable={false}
          showPagination={false}
          className="table__reactTable"
          height={this.state.height}
          data={tableData}
          columns={tableColumns}
        />
      </div>
    )
  }
}

export default ReactTableWrapper;