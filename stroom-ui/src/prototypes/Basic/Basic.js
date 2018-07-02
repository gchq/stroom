import React from 'react';

import './Basic.css';

const Basic = props => <span className="basic">Most Basic Element</span>;

export const ExpandToFillManual = props => (
  <div className="expand-manual__container">
    <div className="expand-manual__sidebar">variable size division</div>
    <div className="expand-manual__content">this division fills the rest of the space</div>
  </div>
);

export const ExpandToFillFlexbox = props => (
  <div className="expand-flex__container">
    <div className="expand-flex__sidebar">variable size division</div>
    <div className="expand-flex__content">this division fills the rest of the space</div>
  </div>
);

export default Basic;
