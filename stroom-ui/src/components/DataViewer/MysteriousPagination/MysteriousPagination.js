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

import { Button, Dropdown, Icon } from 'semantic-ui-react';

import uuidv4 from 'uuid/v4';

const defaultNumberOfVisiblePages = 5;

const dropdownOptions = [
  {
    text: 10,
    value: 10,
  },
  {
    text: 20,
    value: 20,
  },
  {
    text: 30,
    value: 30,
  },
  {
    text: 40,
    value: 40,
  },
  {
    text: 50,
    value: 50,
  },
  {
    text: 100,
    value: 100,
  },
];

const MysteriousPagination = ({
  onPageChange, pageOffset, pageSize, visiblePages,
}) => {
  if (visiblePages === undefined) {
    visiblePages = defaultNumberOfVisiblePages;
  }
  // We want something like [1,2,3,?,?] or [4,5,6,7,8]
  const pageOffsetIndexFromOne = pageOffset + 1;
  let pages = Array(visiblePages).fill('?');
  let modifiedIndex = pageOffsetIndexFromOne - visiblePages;
  if (modifiedIndex < 0) {
    modifiedIndex = 0;
  }
  pages = pages.map(() => {
    modifiedIndex += 1;
    if (modifiedIndex <= pageOffsetIndexFromOne) {
      return modifiedIndex;
    }
    return '?';
  });
  return (
    <React.Fragment>
      <Button.Group size="mini" basic>
        <Button
          aria-label="Go to the previous page"
          icon
          disabled={pageOffsetIndexFromOne === 1}
          onClick={() => onPageChange(pageOffset - 1, pageSize)}
        >
          <Icon name="left arrow" />
        </Button>
        {pages.map(pageValue => (
          <Button
            aria-label={`Go to page ${pageValue}`}
            key={pageValue === '?' ? uuidv4() : pageValue}
            disabled={pageValue === '?'}
            active={pageValue === pageOffsetIndexFromOne}
            className="MysteriousPagination__paginationButton"
            size="mini"
            onClick={(_, data) => {
              // data.children shows the index from one (the display index)
              // so we need to modify that for the search request.
              onPageChange(data.children - 1, pageSize);
            }}
          >
            {pageValue}
          </Button>
        ))}

        <Button
          aria-label="Go to the next page"
          icon
          onClick={() => onPageChange(pageOffset + 1, pageSize)}
        >
          <Icon name="right arrow" />
        </Button>
      </Button.Group>
      <div className="MysteriousPagination__pageSizeSelection">
        <div>Show&nbsp;</div>
        <Dropdown
          floating
          inline
          options={dropdownOptions}
          value={pageSize}
          onChange={(_, data) => {
            onPageChange(pageOffset, data.value);
          }}
        />
      </div>
    </React.Fragment>
  );
};

MysteriousPagination.propTypes = {
  onPageChange: PropTypes.func.isRequired,
  pageOffset: PropTypes.number.isRequired,
  pageSize: PropTypes.oneOf(dropdownOptions.map(option => option.value)).isRequired,
  visiblePages: PropTypes.number,
};

export default MysteriousPagination;
