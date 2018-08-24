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

import moment from 'moment'
import './Graph.css'
import {
  Grid,
  Form,
  Label,
  Table,
  Progress
} from 'semantic-ui-react'

import 'react-datepicker/dist/react-datepicker.css'
import 'react-datepicker/dist/react-datepicker-cssmodules.css'

import Header from 'prototypes/Header'

const dummyTrackers = [
  {
    name: 'FANTASTIC_PIPELINE_ALL_CAPS_FOR_SOME_REASON',
    trackerMs: moment()
      .subtract(4, 'hours')
      .toISOString(),
    progress: 76,
    lastPollAge: 6.1,
    completed: false,
    enabled: true,
    priority: 5
  },
  {
    name: 'FANTASTIC_PIPELINE_2',
    trackerMs: moment()
      .subtract(3, 'days')
      .toISOString(),
    progress: 1,
    lastPollAge: 1,
    completed: false,
    enabled: true,
    priority: 18
  },
  {
    name: 'FANTASTIC_PIPELINE_3',
    trackerMs: moment()
      .subtract(18, 'hours')
      .toISOString(),
    progress: 100,
    lastPollAge: 300,
    completed: true,
    enabled: false,
    priority: 10
  }
]

// const sortOptions = [
//   { key: "trackerMs", value: "trackerMs", text: "Created tasks up to" },
//   {
//     key: "trackerPercentage",
//     value: "trackerPercentage",
//     text: "Percentage complete"
//   },
//   { key: "lastPollAge", value: "lastPollAge", text: "Last polled" }
// ];

// const sortDirectionOptions = [
//   { key: "asc", value: "asc", text: "Ascending" },
//   { key: "desc", value: "desc", text: "Descending" }
// ];

class Graph extends Component {
  // Set up some defaults
  state = {
    showCompleted: false,
    // orderBy: "trackerMs",
    // sortDirection: "desc",
    data: dummyTrackers,
    column: 'progress',
    direction: 'descending'
  };

  handleShowCompletedToggle = (e, toggleProps) => {
    this.setState({ showCompleted: toggleProps.checked })
  };

  // handleSortChange = (e, orderDropdownProps) => {
  //   this.setState({ orderBy: orderDropdownProps.value });
  // };

  // handleSortDirectionChange = (e, sortDirectionDropdownProps) => {
  //   this.setState({ sortDirection: sortDirectionDropdownProps.value });
  // };

  handleSort = clickedColumn => () => {
    const { column, data, direction } = this.state

    if (column !== clickedColumn) {
      this.setState({
        column: clickedColumn,
        // data: _sortBy(data, [clickedColumn]),
        data: data.sort((l, r) => l[clickedColumn] > r[clickedColumn]),
        direction: 'ascending'
      })

      return
    }

    this.setState({
      data: data.reverse(),
      direction: direction === 'ascending' ? 'descending' : 'ascending'
    })
  };

  render () {
    const { column, data, direction, showCompleted } = this.state

    return (
      <div className='App'>
        <Grid>
          <Header />

          <Grid.Column width={4} />
          <Grid.Column width={8}>
            <Form>
              <Form.Group inline>
                <Form.Checkbox
                  inline
                  label='Include completed?'
                  toggle
                  onChange={this.handleShowCompletedToggle}
                />
              </Form.Group>
            </Form>
          </Grid.Column>
          <Grid.Column width={4} />

          <Grid.Column width={16}>
            <Table sortable basic='very' className='tracker-table'>
              <Table.Header>
                <Table.Row>
                  <Table.HeaderCell
                    sorted={column === 'name' ? direction : null}
                    onClick={this.handleSort('name')}
                  >
                    Name
                  </Table.HeaderCell>
                  <Table.HeaderCell
                    sorted={column === 'priority' ? direction : null}
                    onClick={this.handleSort('priority')}
                  >
                    Priority
                  </Table.HeaderCell>
                  <Table.HeaderCell
                    sorted={column === 'progress' ? direction : null}
                    onClick={this.handleSort('progress')}
                  >
                    Progress
                  </Table.HeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data
                  .filter(tracker => {
                    if (showCompleted) {
                      return true
                    } else {
                      return !tracker.completed
                    }
                  })
                  .map(({ name, priority, progress }) => (
                    <Table.Row key={name}>
                      <Table.Cell className='name-column' textAlign='right' width={7}>
                        {name}
                      </Table.Cell>
                      <Table.Cell className='priority-column' textAlign='center' width={1}>
                        <Label circular color='green'>
                          {priority}
                        </Label>
                      </Table.Cell>
                      <Table.Cell className='progress-column' width={8}>
                        <Progress percent={progress} indicating />
                      </Table.Cell>
                    </Table.Row>
                  ))}
              </Table.Body>
            </Table>
          </Grid.Column>
        </Grid>
      </div>
    )
  }
}

export default Graph
