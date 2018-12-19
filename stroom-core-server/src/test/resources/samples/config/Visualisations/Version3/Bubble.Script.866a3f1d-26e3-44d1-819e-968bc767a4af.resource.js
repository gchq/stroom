/*
 * Copyright 2016 Crown Copyright
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
if (!visualisations) {
    var visualisations = {};
}

visualisations.Bubble = function(containerNode) {
    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;

    var initialised = false;

    var visSettings;
    var visContext;

    if (containerNode){
        var element = containerNode;
    } else {
        var element = window.document.createElement("div");
    }
    this.element = element;

    var grid = new visualisations.GenericGrid(element);

    var d3 = window.d3;
    var margins = commonConstants.margins();
    // Create a colour set.
    var colour = commonConstants.categoryGoogle();

    var width;
    var height;
    var canvas;
    var svg;
    var tweenDuration = commonConstants.transitionDuration;
    var tip;
    var inverseHighlight;
    var bubbleLayout;
    var zoom;

    //one off initialisation of all the local variables, including
    //appending various static dom elements
    var initialise = function() {
        initialised = true;

        width = commonFunctions.gridAwareWidthFunc(true, containerNode, element);
        height = commonFunctions.gridAwareHeightFunc(true, containerNode, element);

        canvas = d3.select(element).append("svg:svg");

        svg = canvas.append("svg:g");

        //Ideally it would be good to reset the mousewheel zoom when we zoom in/out of a grid cell
        zoom = d3.behavior.zoom()
            .scaleExtent([0.1,100])
            .on("zoom", commonFunctions.zoomed(svg));

        canvas.call(zoom);

        bubbleLayout = d3.layout.pack()
            .value(function(d) {
                return d.value;
            })
            .padding(1)
            .size([width, height]);

        //Set up the bar highlighting and hover tip
        if (typeof(tip) == "undefined") {
            inverseHighlight = commonFunctions.inverseHighlight();
            tip = inverseHighlight.tip()
                .html(function(tipData) { 
                    var html = inverseHighlight.htmlBuilder()
                        .addTipEntry("Series",commonFunctions.autoFormat(tipData.values.series, visSettings.seriesDateFormat))
                        .addTipEntry("Name",commonFunctions.autoFormat(tipData.values.name, visSettings.nameDateFormat))
                        .addTipEntry("Value",commonFunctions.autoFormat(tipData.values.value))
                        .build();
                    return html;
                });
        }
    };

    //Method to allow the grid to call back in to get new instances for each cell
    this.getInstance = function(containerNode) {
        return new visualisations.Bubble(containerNode);
    };

    var objData;
    var visData;

    this.setData = function(context, settings, data) {
        if (data && data !== null){
            // If the context already has a colour set then use it, otherwise set it
            // to use this one.
            if (context) {
                if (context.color) {
                    colour = context.color;
                } else {
                    //context.color = colour;
                }
            }

            if (settings){
                //Inspect settings to determine which axes to synch, if any.
                //Change the settings property(s) used according to the vis
                var synchedFields = [];
                if (commonFunctions.isTrue(settings.synchXAxis)){
                    synchedFields.push(0);
                }
                if (commonFunctions.isTrue(settings.synchYAxis)){
                    synchedFields.push(1);
                }

                if (commonFunctions.isTrue(settings.synchSeries)) {
                    //series are synched so setup the colour scale domain and add it to the context
                    //so it can be passed to each grid cell vis
                    context.color = colour;
                } else {
                    //ensure there is no colour scale in the context so each grid cel vis can define its own
                    delete context.color;
                }

                //Get grid to construct the grid cells and for each one call back into a 
                //new instance of this to build the visualisation in the cell
                //The last array arg allows you to synchronise the scales of fields
                grid.buildGrid(context, settings, data, this, commonConstants.transitionDuration, synchedFields);
            }
        }
    };

    //Public entry point for the Grid to call back in to set the cell level data on the cell level 
    //visualisation instance.
    //data will only contain the branch of the tree for this cell
    this.setDataInsideGrid = function(context, settings, data) {
        if (!initialised){
            initialise();
        }

        // If the context already has a colour set then use it
        if (context) {
            visContext = context;
            if (context.color) {
                colour = context.color;
            }
        }
        if (settings){
            visSettings = settings;
            var visibleValues = data.visibleValues();
            if (commonFunctions.isTrue(settings.flattenSeries)){
                objData = flatClasses(visibleValues);
            } else {
                objData = groupedClasses(visibleValues);
            }
        }

        visData = data;
        update(0);
    };

    var update = function(duration) {
        if (objData) {
            width = commonFunctions.gridAwareWidthFunc(true, containerNode, element, margins);
            height = commonFunctions.gridAwareHeightFunc(true, containerNode, element, margins);
            fullWidth = commonFunctions.gridAwareWidthFunc(false, containerNode, element, margins);
            fullHeight = commonFunctions.gridAwareHeightFunc(false, containerNode, element, margins);

            if (width > 0 && height > 0) {
                bubbleLayout.size([width, height]);
                canvas
                    .attr("width", fullWidth)
                    .attr("height", fullHeight);
            }

            svg.call(tip);

            //sort by the series name, and then the value of the bubble
            //so bubbles of the same series are together and arranged in 
            //size order within the series set
            bubbleLayout.sort(function(a, b) { 
                if (a.series < b.series) {
                    return -1;
                } else if (a.series > b.series){
                    return 1;
                } else {
                    return (a.value - b.value);
                }
            });

            var bubbleData = bubbleLayout.nodes(objData)
                .filter(function(d) {
                    //return !d.children;
                    //return true;
                    return !d.depth == 0;
                });

            //data binding
            var nodes = svg.selectAll("g.vis-node-element")
                .data(bubbleData, function(d) {
                    //compound data key
                    var key = d.series + "~#~" + d.name; 
                    //console.log("key: " + key);
                    return key;
                });

            var sizeFunc = function(d) {
                return 2 * d.r;
            };

            //New bubbles
            nodes.enter()
                .append("svg:g")
                .attr("class", "vis-node-element")
                .attr("opacity", "1e-6")
                .attr("transform", function(d) {
                    return "translate(" + d.x + "," + d.y + ")";
                })
                .call(function(parentNode) {
                    parentNode.append("svg:circle");

                    if (commonFunctions.isTrue(visSettings.showLabels)) {
                        var subSvg = parentNode.append("svg:svg")

                        var text = subSvg.append("svg:text")
                            .attr("text-anchor", "middle")
                            .attr("dy", "1em")
                            .style("pointer-events", "none")
                            .style("font-size", "20px")
                            .style("text-rendering", "geometricPrecision")
                            .text(function(d) { 
                                if (d.name != null) {
                                    return commonFunctions.autoFormat(d.name, visSettings.nameDateFormat);
                                } else {
                                    return commonFunctions.autoFormat(d.series, visSettings.seriesDateFormat);
                                }
                            });
                    }
                });

            //Changed bubbles, including new ones
            //So that we can 'effectively' scale the label text to fit in the circle
            //we build an svg element to the size of the circle with a viewport the size of the
            //text that sits within it.
            nodes.transition()
                .duration(tweenDuration)
                .attr("opacity", function(d) {
                    return commonFunctions.isTrue(visSettings.flattenSeries) ? "1" : "0.7";
                })
                .attr("transform", function(d) {
                    return "translate(" + d.x + "," + d.y + ")";
                })
                .each(function(d) {
                    var node = d3.select(this);
                    var circle = node.select("circle")
                        .attr("class", commonFunctions.makeColouredElementClassStringFunc(function(d) { return d.series; }))
                        .attr("r", function(d) {
                            return d.r;
                        })
                        .style("fill", function(d) { 
                            return colour(d.series); 
                        });

                    if (commonFunctions.isTrue(visSettings.showLabels)){
                        var text = node.select("text");

                        var bb = text.node().getBBox();
                        var bbWidth = bb.width + 15;
                        var bbHeight = bb.height;

                        var subSvg = node.select("svg")
                            .attr("width", function(d) {
                                return d.r * 2;
                            })
                            .attr("height", function(d) {
                                return d.r * 2;
                            })
                            .attr("x", function(d) {
                                return -d.r;
                            })
                            .attr("y", function(d) {
                                return -d.r;
                            })
                            .attr("viewBox", function(d) {
                                return "0 0 " + bbWidth + " " + bbHeight;
                            });

                        text.attr("transform", "translate(" + (bbWidth / 2) + "," + 0 + ")");
                    }
                });

            //removed bubbles
            nodes.exit()
                .transition()
                .duration(tweenDuration)
                .attr("opacity", "1e-6")
                .remove();

            commonFunctions.addDelegateEvent(svg, "mouseover", "circle", inverseHighlight.makeInverseHighlightMouseOverHandler(null, visData.types, svg, "circle"));
            commonFunctions.addDelegateEvent(svg, "mouseout", "circle", inverseHighlight.makeInverseHighlightMouseOutHandler(svg, "circle"));

            //as this vis supports scrolling and panning by mousewheel and mousedown we need to remove the tip when the user
            //pans or zooms
            commonFunctions.addDelegateEvent(svg, "mousewheel", "circle", inverseHighlight.makeInverseHighlightMouseOutHandler(svg, "circle"));
            commonFunctions.addDelegateEvent(svg, "mousedown", "circle", inverseHighlight.makeInverseHighlightMouseOutHandler(svg, "circle"));
        }
    };

    this.resize = function() {
        commonFunctions.resize(grid, update, element, margins, width, height);
    };

    this.getColourScale = function(){
        return colour;
    };

    this.getLegendKeyField = function() {
        //This vis is series based so return null
        return null;
    };

    // Returns a flattened hierarchy containing all leaf nodes under the root.
    function flatClasses(arr) {
        var par = {
            parent: null, 
            children: null
        };
        var classes = [];
        for (i=0;i < arr.length; i++)
        {
            for (j=0; j < arr[i].values.length; j++) {
                classes.push({
                    parent: par, 
                    children: null, 
                    series: arr[i].key, 
                    name: arr[i].values[j][0], 
                    value: arr[i].values[j][1]
                });
            }
        }
        par.children = classes;
        return par;
    };

    //creates a nested struture for the pack layout to work with
    function groupedClasses(arr) {
        var par = {
            parent: null, 
            children: []
        };
        arr.forEach(function(d) {
            convertEntry(d, par);
        });
        return par;
    };

    //converts a d3.nest() entry in the object structure required for the pack layout
    function convertEntry(entry, theParent) {
        if (entry.key) {
            //is branch
            var branch = {
                parent: theParent, 
                children: [], 
                series: entry.key, 
                name: null
            };
            entry.values.forEach(function(d) {
                convertEntry(d, branch);
            });
            theParent.children.push(branch);
            return branch;
        } else {
            //is leaf
            var leaf = {
                parent: theParent, 
                children: null, 
                series: theParent.series, 
                name: entry[0], 
                value: entry[1]
            };
            theParent.children.push(leaf);
        }
    };

};


