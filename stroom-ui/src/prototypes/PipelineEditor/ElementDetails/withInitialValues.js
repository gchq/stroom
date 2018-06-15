// /*
//  * Copyright 2018 Crown Copyright
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
// import React, { Component } from 'react';
// import PropTypes from 'prop-types';

// import { connect } from 'react-redux';

// import { getInitialValues } from './elementDetailsUtils';

// /**
//  * This is a Higher Order Component
//  * https://reactjs.org/docs/higher-order-components.html
//  *
//  * It provides the pipeline and element definitions
//  * by connecting to the redux store and using a provided
//  * pipelineId and elementId to look them up.
//  *
//  * @param {React.Component} WrappedComponent
//  */
// export function withInitialValues() {
//   return (WrappedComponent) => {
//     const WithInitialValues = class extends Component {
//       static propTypes = {
//         // pipelineId: PropTypes.string.isRequired,
//         // elementId: PropTypes.string,
//         elements: PropTypes.object.isRequired,
//         selectedElementId: PropTypes.string,
//       };

//       state = {
//         initialValues: undefined,
//       };

//       static getDerivedStateFromProps(nextProps, prevState) {
//         const pipeline = nextProps.pipelines[nextProps.pipelineId];

//         const element = pipeline.pipeline.elements.add.find(e => e.id === nextProps.elementId);

//         const elementTypeProperties = nextProps.elements.elementProperties[element.type];

//         const elementProperties = pipeline.pipeline.properties.add.filter(property => property.element === nextProps.elementId);
//         const initialValues = getInitialValues(elementTypeProperties, elementProperties);
//         // const pipeline = nextProps.pipelines[nextProps.pipelineId];
//         //  = {};
//         // let element;
//         // let elementDefinition;

//         // if (pipeline) {
//         //   element = pipeline.pipeline.elements.add.find(e => e.id === nextProps.elementId);
//         //   if (element) {
//         //     elementDefinition = Object.values(nextProps.elements.elements).find(e => e.type === element.type);
//         //   }
//         // }

//         return {
//           initialValues,
//         };
//       }

//       render() {
//         // if (!!this.state.pipeline && !!this.state.element) {
//         return <WrappedComponent {...this.state} {...this.props} />;
//         // }
//         // return null;
//       }
//     };

//     return connect(
//       state => ({
//         pipelines: state.pipelines,
//         elements: state.elements,
//       }),
//       {
//         // actions
//       },
//     )(WithInitialValues);
//   };
// }
