/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.pipeline.SupportsCodeInjection;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.filter.SAXEventRecorder;
import stroom.pipeline.filter.SAXRecordDetector;
import stroom.pipeline.filter.SplitFilter;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.parser.AbstractParser;
import stroom.pipeline.reader.AbstractInputElement;
import stroom.pipeline.reader.AbstractReaderElement;
import stroom.pipeline.reader.InputStreamRecordDetectorElement;
import stroom.pipeline.reader.ReaderRecordDetectorElement;
import stroom.pipeline.reader.ReaderRecorder;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.source.SourceElement;
import stroom.pipeline.stepping.ElementMonitor;
import stroom.pipeline.stepping.Recorder;
import stroom.pipeline.stepping.SteppingController;
import stroom.pipeline.stepping.SteppingFilter;
import stroom.pipeline.writer.OutputRecorder;
import stroom.task.api.Terminator;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.ElementId;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@PipelineScoped
public class PipelineFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineFactory.class);
    private final ElementRegistryFactory pipelineElementRegistryFactory;
    private final ElementFactory elementFactory;
    private final ProcessorFactory processorFactory;
    private final ErrorReceiverProxy errorReceiverProxy;

    @Inject
    public PipelineFactory(final ElementRegistryFactory pipelineElementRegistryFactory,
                           final ElementFactory elementFactory,
                           final ProcessorFactory processorFactory,
                           final ErrorReceiverProxy errorReceiverProxy) {
        this.pipelineElementRegistryFactory = pipelineElementRegistryFactory;
        this.elementFactory = elementFactory;
        this.processorFactory = processorFactory;
        this.errorReceiverProxy = errorReceiverProxy;

        if (processorFactory == null) {
            throw new NullPointerException("processorFactory is null");
        }
    }

    /**
     * Convenience method for non stepping mode. Used only for testing.
     */
    public Pipeline create(final PipelineData pipelineData,
                           final Terminator terminator) {
        return create(pipelineData, terminator, null);
    }

    public Pipeline create(final PipelineData pipelineData,
                           final Terminator terminator,
                           final SteppingController controller) {
        final ElementRegistry pipelineElementRegistry = pipelineElementRegistryFactory.get();

        // If we are stepping then we don't want to use the cache.
        // Create an instance of each element.
        final Map<String, Element> elementInstances = new HashMap<>();
        final Map<Element, PipelineElementType> elementTypeMap = new HashMap<>();
        final Map<String, Set<String>> linkSets = new HashMap<>();

        for (final PipelineElement element : pipelineData.getAddedElements()) {
            LOGGER.debug("create() - loading element {}", element);

            final Class<Element> elementClass = pipelineElementRegistry.getElementClass(element.getType());

            if (elementClass == null) {
                throw new PipelineFactoryException("Unable to load elementClass for type " + element.getType());
            }

            final Element elementInstance = elementFactory.getElementInstance(elementClass);

            if (elementInstance == null) {
                throw new PipelineFactoryException("Unable to load elementInstance for class " + elementClass);
            }

            // Set the id on the pipeline element for use in tracing
            // errors, intercepting input/output etc.
            elementInstance.setElementId(element.getElementId());

            // Set an object to provide quick processing termination if needed.
            elementInstance.setTerminator(terminator);

            // Set the properties on this instance.
            setProperties(pipelineElementRegistry,
                    element.getId(),
                    element.getType(),
                    elementInstance,
                    pipelineData,
                    controller);

            // Set the pipeline references on this instance.
            setPipelineReferences(pipelineElementRegistry, element.getId(), element.getType(), elementInstance,
                    pipelineData);

            // Store the instance.
            elementInstances.put(element.getId(), elementInstance);
            elementTypeMap.put(elementInstance, pipelineElementRegistry.getElementType(element.getType()));

            // Record links.
            final Set<String> linkSet = new HashSet<>();
            linkSets.put(element.getId(), linkSet);
            for (final PipelineLink link : pipelineData.getAddedLinks()) {
                if (link.getFrom().equals(element.getId())) {
                    linkSet.add(link.getTo());
                }
            }
        }

        // Get the source element.
        final SourceElement sourceElement = (SourceElement) elementInstances.get("Source");
        if (sourceElement == null) {
            throw new PipelineFactoryException("The pipeline has no source element");
        }

        // Get the split depth to use with the stepping controller.
        int controllerSplitDepth = 1;
        if (controller != null) {
            controllerSplitDepth =
                    getSplitDepth(elementInstances, elementTypeMap, linkSets, sourceElement.getElementId().getId());
            controllerSplitDepth = Math.max(controllerSplitDepth, 1);
        }

        // Link the instances.
        link(elementInstances,
                elementTypeMap,
                linkSets,
                controller,
                sourceElement,
                sourceElement.getElementId(),
                controllerSplitDepth);

        // We need to create a root element that will be a target for the input
        // stream.
        TakesInput root = sourceElement;

        // Perform the last bit of setup if we are stepping.
        if (controller != null) {
            // If we still haven't added a record detector then add one to the
            // input.
            if (controller.getRecordDetector() == null) {
                final InputStreamRecordDetectorElement recordDetector = new InputStreamRecordDetectorElement();
                controller.setRecordDetector(recordDetector);
                recordDetector.addTarget(sourceElement);
                root = recordDetector;
            }

            controller.getRecordDetector().setController(controller);
        }

        return new PipelineImpl(processorFactory, elementInstances, root, controller != null);
    }

    /**
     * Set the properties on the newly created element instance.
     */
    private void setProperties(final ElementRegistry pipelineElementRegistry,
                               final String id,
                               final String elementType,
                               final Object elementInstance,
                               final PipelineData pipelineData,
                               final SteppingController controller) {
        // Set the properties on this instance.
        for (final PipelineProperty property : pipelineData.getAddedProperties()) {
            if (property.getElement().equals(id)) {
                setProperty(pipelineElementRegistry, id, elementType, elementInstance, property.getName(),
                        property.getValue(), controller);
            }
        }
    }

    /**
     * Code for properties.
     */
    public static void setProperty(final ElementRegistry pipelineElementRegistry,
                                   final String id,
                                   final String elementType,
                                   final Object elementInstance,
                                   final String propertyName,
                                   final PipelinePropertyValue value,
                                   final SteppingController controller) {
        // Some methods might be removed so ignore them if they don't exist.
        final Method method = pipelineElementRegistry.getMethod(elementType, propertyName);

        if (method != null) {
            try {
                // Allow this method to be invoked.
                method.setAccessible(true);

                Object obj = null;
                if (value != null) {
                    final Class<?> paramType = method.getParameterTypes()[0];
                    if (boolean.class.isAssignableFrom(paramType) || Boolean.class.isAssignableFrom(paramType)) {
                        obj = value.getBoolean();
                    } else if (int.class.isAssignableFrom(paramType) || Integer.class.isAssignableFrom(paramType)) {
                        obj = value.getInteger();
                    } else if (long.class.isAssignableFrom(paramType) || Long.class.isAssignableFrom(paramType)) {
                        obj = value.getLong();
                    } else if (String.class.isAssignableFrom(paramType)) {
                        obj = value.getString();
                    } else if (DocRef.class.isAssignableFrom(paramType)) {
                        // Load an entity by id.
                        final DocRef docRef = value.getEntity();
                        if (docRef != null) {
                            obj = docRef;

                            // Modify properties of element instance if we are
                            // stepping and have code to insert.
                            if (controller != null) {
                                final PipelineStepRequest request = controller.getRequest();
                                if (request.getCode() != null && !request.getCode().isEmpty()) {
                                    final String code = request.getCode().get(id);
                                    if (code != null) {
                                        if (elementInstance instanceof
                                                final SupportsCodeInjection supportsCodeInjection) {
                                            supportsCodeInjection.setInjectedCode(code);
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        throw new PipelineFactoryException("Unknown param type: " + paramType);
                    }
                }

                method.invoke(elementInstance, obj);

            } catch (final InvocationTargetException | IllegalAccessException e) {
                throw new PipelineFactoryException(e);
            }
        }
    }

    /**
     * Set the pipeline references on the newly created element instance.
     */
    private void setPipelineReferences(final ElementRegistry pipelineElementRegistry,
                                       final String id,
                                       final String elementType,
                                       final Object elementInstance,
                                       final PipelineData pipelineData) {
        // Set the properties on this instance.
        for (final PipelineReference pipelineReference : pipelineData.getAddedPipelineReferences()) {
            if (pipelineReference.getElement().equals(id)) {
                setPipelineReference(pipelineElementRegistry, elementType, elementInstance, pipelineReference.getName(),
                        pipelineReference);
            }
        }
    }

    /**
     * Code for pipeline references.
     */
    private void setPipelineReference(final ElementRegistry pipelineElementRegistry,
                                      final String elementType,
                                      final Object elementInstance,
                                      final String propertyName,
                                      final PipelineReference pipelineReference) {
        final Method method = pipelineElementRegistry.getMethod(elementType, propertyName);

        if (method != null) {
            try {
                // Allow this method to be invoked.
                method.setAccessible(true);
                method.invoke(elementInstance, pipelineReference);

            } catch (final InvocationTargetException | IllegalAccessException e) {
                throw new PipelineFactoryException(e);
            }
        }
    }

    /**
     * Link element instances together to form the pipeline.
     */
    private void link(final Map<String, Element> elementInstances,
                      final Map<Element, PipelineElementType> elementTypeMap,
                      final Map<String, Set<String>> linkSets,
                      final SteppingController controller,
                      final Element parentElement,
                      final ElementId parentElementId,
                      final int controllerSplitDepth) {
        // Get the child elements of the supplied 'from' element id that we want
        // to work with.
        final List<Element> childElements = getChildElements(parentElementId.getId(), elementInstances, elementTypeMap,
                linkSets, controller);

        // Loop over the child elements and link them to the parent.
        for (final Element childElement : childElements) {
            final ElementId elementId = childElement.getElementId();
            final PipelineElementType elementType = elementTypeMap.get(childElement);

            Fragment fragment = new Fragment(childElement);

            // If we are in stepping mode then insert a recorder before the
            // element and one after. Once this is done add the recorders to an
            // element monitor and register it with the stepping controller.
            // Finally insert a record detector if appropriate for the current
            // element to control stepping.
            if (controller != null) {
                if (parentElement instanceof OutputRecorder) {
                    // If the parent element is already an output recorder used
                    // to replace a destination then reuse the same output recorder
                    // for any downstream elements.
                    fragment = new Fragment(parentElement);
                    addMonitor(elementId, elementType, childElement, fragment, controller);

                } else {
                    fragment = insertRecorder(elementId, elementType, fragment, true, controller,
                            controllerSplitDepth);
                    fragment = insertRecorder(elementId, elementType, fragment, false, controller,
                            controllerSplitDepth);
                    addMonitor(elementId, elementType, childElement, fragment, controller);
                    fragment = insertRecordDetector(elementType, fragment, true, controller);
                    fragment = insertRecordDetector(elementType, fragment, false, controller);
                }
            }

            // Continue to link the children of this child.
            link(elementInstances,
                    elementTypeMap,
                    linkSets,
                    controller,
                    fragment.getOut(),
                    elementId,
                    controllerSplitDepth);

            // Now set the target of the parent element to be the 'wrapped'
            // child to complete the link.
            if (parentElement != null && parentElement != fragment.getIn()) {
                if (parentElement instanceof final HasTargets hasTargets) {
                    if (childElement instanceof Target) {
                        if (fragment.getIn() instanceof final Target target) {
                            hasTargets.addTarget(target);
                        }
                    } else {
                        throw new PipelineFactoryException("Attempt to link to an element that is not a target: "
                                                           + parentElement.getElementId() + " > " + elementId);
                    }
                } else {
                    throw new PipelineFactoryException(
                            "Attempt to link from an element that cannot target another element: "
                            + parentElement.getElementId());
                }
            }
        }
    }

    /**
     * Get the split depth for use in stepping.
     */
    private int getSplitDepth(final Map<String, Element> elementInstances,
                              final Map<Element, PipelineElementType> elementTypeMap,
                              final Map<String, Set<String>> linkSets,
                              final String parentElementId) {
        // Get the child elements of the supplied 'from' element id that we want
        // to work with.
        final List<Element> childElements = getChildElements(parentElementId, elementInstances, elementTypeMap,
                linkSets, null);

        // Loop over the child elements and link them to the parent.
        for (final Element childElement : childElements) {
            if (childElement instanceof final SplitFilter splitFilter) {
                return splitFilter.getSplitDepth();
            }
            final int childSplitDepth =
                    getSplitDepth(elementInstances, elementTypeMap, linkSets, childElement.getElementId().getId());
            if (childSplitDepth != -1) {
                return childSplitDepth;
            }
        }

        return -1;
    }

    /**
     * Get child elements for the given parent or descendants if some
     * intermediate children are to be excluded due to stepping mode being used.
     *
     * @param fromElementId    The id of the element to get children or descendant elements
     *                         for. If this is null then root elements will be returned.
     * @param elementInstances A map of element ids to element instances.
     * @param elementTypeMap   A map of element instances to element types.
     * @param linkSets         A map of links keyed by element ids to link from, to sets of
     *                         element ids that are linked to.
     * @param controller       The stepping controller. Null if we are not stepping.
     * @return A list of immediate child or descendant elements for the supplied
     * parent element id.
     */
    private List<Element> getChildElements(final String fromElementId,
                                           final Map<String, Element> elementInstances,
                                           final Map<Element, PipelineElementType> elementTypeMap,
                                           final Map<String, Set<String>> linkSets,
                                           final SteppingController controller) {
        List<Element> childElements = Collections.emptyList();

        final Set<String> toElementIdSet = linkSets.get(fromElementId);
        if (toElementIdSet != null && !toElementIdSet.isEmpty()) {
            childElements = new ArrayList<>(toElementIdSet.size());

            for (final String toElementId : toElementIdSet) {
                final Element to = elementInstances.get(toElementId);
                // If the element we are linking to does not exist then we can't
                // link.
                if (to != null) {
                    // If we are stepping then filter the descendants so we only
                    // include ones that are appropriate for stepping.
                    if (controller != null) {
                        final PipelineElementType elementType = elementTypeMap.get(to);
                        if (elementType.hasRole(PipelineElementType.VISABILITY_STEPPING)) {
                            childElements.add(to);
                        } else {
                            childElements.addAll(getChildElements(toElementId, elementInstances, elementTypeMap,
                                    linkSets, controller));
                        }
                    } else {
                        childElements.add(to);
                    }
                }
            }
        }

        return childElements;
    }

    /**
     * Creates an appropriate input or output recorder for the supplied pipeline
     * fragment and modifies the fragment. If it is an input recorder then the
     * supplied fragment input is attached as a target of the recorder. If it is
     * an output recorder it is attached as the target of the supplied fragment
     * output.
     *
     * @param elementId   The id of the element that we might be inserting a recorder
     *                    before or after.
     * @param elementType The type of the element that we might be inserting a recorder
     *                    before or after.
     * @param fragment    The current pipeline fragment we are modifying.
     * @param input       True if we are modifying the input element of the fragment,
     *                    false if we are modifying the output.
     * @param controller  The stepping controller.
     * @return A modified pipeline fragment if a recorder was added or the
     * original if it remains unchanged.
     */
    private Fragment insertRecorder(final ElementId elementId,
                                    final PipelineElementType elementType,
                                    final Fragment fragment,
                                    final boolean input,
                                    final SteppingController controller,
                                    final int controllerSplitDepth) {
        Fragment result = fragment;

        // Get any filter settings that might be applied to XML output.
        SteppingFilterSettings steppingFilterSettings = null;
        if (!input) {
            steppingFilterSettings = controller.getRequest().getStepFilterSettings(elementId.getId());
        }

        if (input) {
            final Element in = fragment.getIn();

            if (in instanceof DestinationProvider) {
                // Replace all destination providers.
                final OutputRecorder outputRecorder = elementFactory.getElementInstance(OutputRecorder.class);
                outputRecorder.setElementId(elementId);
                result = new Fragment(outputRecorder);

            } else if (in instanceof final AbstractInputElement filter) {
                final ReaderRecorder recorder = new ReaderRecorder(errorReceiverProxy);
                recorder.setElementId(elementId);
                recorder.setTarget(filter);
                result = new Fragment(recorder, fragment.getOut());

            } else if (in instanceof final AbstractReaderElement filter) {
                final ReaderRecorder recorder = new ReaderRecorder(errorReceiverProxy);
                recorder.setElementId(elementId);
                recorder.setTarget(filter);
                result = new Fragment(recorder, fragment.getOut());

            } else if (in instanceof final AbstractParser parser) {
                final ReaderRecorder recorder = new ReaderRecorder(errorReceiverProxy);
                recorder.setElementId(elementId);
                recorder.setTarget(parser);
                result = new Fragment(recorder, fragment.getOut());

            } else if (in instanceof final XMLFilter filter && elementType.hasRole(PipelineElementType.ROLE_MUTATOR)) {

                // Create stepping filter.
                final SAXEventRecorder recorder = elementFactory.getElementInstance(SAXEventRecorder.class);
                recorder.setElementId(elementId);
                recorder.setTarget(filter);

                // Initialise stepping filter settings.
                recorder.setSettings(steppingFilterSettings);

                result = new Fragment(recorder, fragment.getOut());
            }

        } else {
            final Element out = fragment.getOut();

            if (out instanceof DestinationProvider) {
                // Replace all destination providers.
                final OutputRecorder outputRecorder = elementFactory.getElementInstance(OutputRecorder.class);
                outputRecorder.setElementId(elementId);
                result = new Fragment(outputRecorder);

            } else if (out instanceof final AbstractInputElement filter) {
                final ReaderRecorder recorder = new ReaderRecorder(errorReceiverProxy);
                recorder.setElementId(elementId);
                filter.setTarget(recorder);
                result = new Fragment(fragment.getIn(), recorder);

            } else if (out instanceof final AbstractReaderElement filter) {
                final ReaderRecorder recorder = new ReaderRecorder(errorReceiverProxy);
                recorder.setElementId(elementId);
                filter.setTarget(recorder);
                result = new Fragment(fragment.getIn(), recorder);

            } else if (out instanceof final AbstractParser parser) {

                // Insert a split filter after the parser to split all XML into
                // single records.
                final SplitFilter splitFilter = elementFactory.getElementInstance(SplitFilter.class);
                splitFilter.setSplitDepth(controllerSplitDepth);
                splitFilter.setSplitCount(controller.getRequest().getStepSize());
                parser.setTarget(splitFilter);

                // Create SAX event recorder.
                final SAXEventRecorder recorder = elementFactory.getElementInstance(SAXEventRecorder.class);
                recorder.setElementId(elementId);

                splitFilter.setTarget(recorder);

                // Initialise stepping filter settings.
                recorder.setSettings(steppingFilterSettings);

                result = new Fragment(fragment.getIn(), recorder);

            } else if (out instanceof final XMLFilter filter && elementType.hasRole(PipelineElementType.ROLE_WRITER)) {

                final OutputRecorder outputRecorder = elementFactory.getElementInstance(OutputRecorder.class);
                outputRecorder.setElementId(elementId);
                ((HasTargets) filter).setTarget(outputRecorder);

                result = new Fragment(fragment.getIn(), outputRecorder);

            } else if (out instanceof final XMLFilter filter &&
                       (elementType.hasRole(PipelineElementType.ROLE_MUTATOR) ||
                        elementType.hasRole(PipelineElementType.ROLE_VALIDATOR))) {

                // Create stepping filter.
                final SAXEventRecorder recorder = elementFactory.getElementInstance(SAXEventRecorder.class);
                recorder.setElementId(elementId);
                ((HasTargets) filter).setTarget(recorder);

                // Initialise stepping filter settings.
                recorder.setSettings(steppingFilterSettings);

                result = new Fragment(fragment.getIn(), recorder);
            }
        }

        return result;
    }

    /**
     * If we are in stepping mode then this method will register an element
     * monitor using the input and output recorders with the stepping
     * controller.
     *
     * @param elementId   The id of the element that we are monitoring.
     * @param elementType The type of the element that we are monitoring.
     * @param element     The actual element instance that we are monitoring.
     * @param fragment    The pipeline fragment for the element to be monitored that may
     *                    include recorders for input and output.
     * @param controller  The stepping controller.
     */
    private void addMonitor(final ElementId elementId,
                            final PipelineElementType elementType,
                            final Element element,
                            final Fragment fragment,
                            final SteppingController controller) {
        final ElementMonitor elementMonitor = new ElementMonitor(elementId, elementType, element);
        if (fragment.getIn() == fragment.getOut()) {
            // In some cases we replace the input and output elements with the
            // same recorder. In these cases we treat the recorder as an output
            // recorder.
            if (fragment.getOut() != null && fragment.getOut() instanceof Recorder) {
                elementMonitor.setOutputMonitor((Recorder) fragment.getOut());
            }
        } else {
            if (fragment.getIn() != null && fragment.getIn() instanceof Recorder) {
                elementMonitor.setInputMonitor((Recorder) fragment.getIn());
            }
            if (fragment.getOut() != null && fragment.getOut() instanceof Recorder) {
                elementMonitor.setOutputMonitor((Recorder) fragment.getOut());
            }
        }

        if (fragment.getOut() != null && fragment.getOut() instanceof SteppingFilter) {
            elementMonitor.setSteppingFilter((SteppingFilter) fragment.getOut());
        }

        controller.registerMonitor(elementMonitor);
    }

    /**
     * Insert a pipeline element that will notify the controller when the end of
     * a record is detected. For XML this is whenever endDocument() is called.
     * For text it it when a new line character is reached.
     *
     * @param elementType The type of the element that we might be inserting a record
     *                    detector before or after.
     * @param fragment    The current pipeline fragment we are modifying.
     * @param input       True if we are modifying the input element of the fragment,
     *                    false if we are modifying the output.
     * @param controller  The stepping controller.
     * @return A modified pipeline fragment if a record detector was added or
     * the original if it remains unchanged.
     */
    private Fragment insertRecordDetector(final PipelineElementType elementType,
                                          final Fragment fragment,
                                          final boolean input,
                                          final SteppingController controller) {
        Fragment result = fragment;

        if (!input) {
            if (elementType.hasRole(PipelineElementType.ROLE_PARSER)) {
                if (controller.getRecordDetector() == null
                    || !(controller.getRecordDetector() instanceof SAXRecordDetector)) {
                    final SAXRecordDetector recordDetector = elementFactory.getElementInstance(SAXRecordDetector.class);
                    controller.setRecordDetector(recordDetector);
                    ((HasTargets) fragment.getOut()).setTarget(recordDetector);
                    result = new Fragment(fragment.getIn(), recordDetector);
                }

            } else if (elementType.hasRole(PipelineElementType.ROLE_READER)) {
                if (controller.getRecordDetector() == null) {
                    final ReaderRecordDetectorElement recordDetector = elementFactory.getElementInstance(
                            ReaderRecordDetectorElement.class);
                    controller.setRecordDetector(recordDetector);
                    ((HasTargets) fragment.getOut()).setTarget(recordDetector);
                    result = new Fragment(fragment.getIn(), recordDetector);
                }
            }
        }

        return result;
    }

    private static class Fragment {

        private final Element in;
        private final Element out;

        Fragment(final Element in, final Element out) {
            this.in = in;
            this.out = out;
        }

        Fragment(final Element element) {
            this.in = element;
            this.out = element;
        }

        public Element getIn() {
            return in;
        }

        public Element getOut() {
            return out;
        }
    }
}
