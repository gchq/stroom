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
visualisations.LineChart = function(containerNode) {

    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;

    var initialised = false;

    var visSettings;
    var visContext;

    var d3 = window.d3;

    if (containerNode){
        var element = containerNode;
    } else {
        var element = window.document.createElement("div");
    }
    this.element = element;

    var grid;

    var margins = commonConstants.margins();
    var colour = commonConstants.categoryGoogle();
    var interpolationMode = "basis";
    var width;
    var height;

    var xSettings;
    var ySettings;
    var xScale;
    var yScale;
    var xAxis;
    var yAxis;
    var highlightedPath;

    var canvas;

    var svg;
    var tip;

    // Add the series data.
    var seriesContainer;

    // Add the x-axis.
    var xAxisContainer;

    // Add the y-axis.
    var yAxisContainer;

    // Add a marker to show a point on a line nearest to the mouse.
    var marker;

    // Add a label to show information relating to the point marked
    // on the line.
    var label;
    var labelX;
    var labelY;

    // Add a rect to capture all mouse events and highlight
    // paths on mouse move.
    var mouseEventRect;

    var visData;

    var initialise = function() {
        initialised = true;

        width = commonFunctions.gridAwareWidthFunc(true, containerNode, element);
        height = commonFunctions.gridAwareHeightFunc(true, containerNode, element);

        canvas = d3.select(element)
            .append("svg:svg");

        svg = canvas.append("svg:g");

        if (typeof(tip) == "undefined") {
            inverseHighlight = commonFunctions.inverseHighlight();
            tip = inverseHighlight.tip()
                .html(function(tipData) { 
                    var html = inverseHighlight.htmlBuilder()
                        .addTipEntry("Name",commonFunctions.autoFormat(tipData.closestKey, visSettings.seriesDateFormat))
                        .addTipEntry("X",commonFunctions.autoFormat(xScale.invert(tipData.closestPos.x)))
                        .addTipEntry("Y",commonFunctions.autoFormat(yScale.invert(tipData.closestPos.y)))
                        .build();
                    return html;
                });
        }
        //Add an invisible rect to capture all the mouse events
        //We have to use this approach so that we can have the tip scan accross
        //each path
        mouseEventRect = svg.append("svg:rect")
            .attr("x",0)
            .attr("y",0)
            .attr("opacity", "0")
            .style("pointer-events", "all")
            .style("cursor", "crosshair");

        // Add the series data.
        seriesContainer = svg.append("svg:g")
            .attr("class", "vis-series")
            .style("pointer-events", "none");

        // Add the x-axis.
        xAxisContainer = svg.append("svg:g")
            .attr("class", "vis-axis" + " xAxis");

        // Add the y-axis.
        yAxisContainer = svg.append("svg:g")
            .attr("class", "vis-axis" + " yAxis");

        // Add a marker to show a point on a line nearest to the mouse.
        marker = svg.append("svg:circle")
            .attr("class", "vis-marker")
            .attr("opacity", "0")
            .attr("r", 5)
            .style("pointer-events", "none");

        // Add a rect to capture all mouse events and highlight
        // paths on mouse move.
        mouseEventRect.on("mousemove", onMouseMove);
        mouseEventRect.on("mousedown", onMouseDown);
    };


    var updateHighlight = function(parentNode) {

        var allElements = d3.select(parentNode.node())
            .selectAll(".vis-coloured-element");

        allElements
            .style("transition", "opacity 0.15s ease")
            .each(function(d,i) {
                var thisPath = d3.select(this);

                if (highlightedPath == null || thisPath.node() == highlightedPath.node()) {
                    thisPath	
                        .style("opacity", 1)
                        .style("transition", "opacity 0.15s ease");
                } else {
                    thisPath
                        .style("opacity", 0.05);
                }
            });
    };

    var toggleMarkerVisibility = function(closest) {
        if (highlightedPath) {
            //make marker visible and move it to the closest point on the path
            marker
                .attr("opacity", "1")
                .style("fill", highlightedPath.style("stroke"));

            tip.show(closest, marker.node());
            tip.style("pointer-events","none");
        } else {
            marker
                .attr("opacity", "0");
            tip.hide();
        }
    };

    var setMarkerPosition = function(closest) {
        if (closest && closest.closestPos){
            marker
                .attr("transform", "translate(" + closest.closestPos.x + "," + closest.closestPos.y + ")");
            tip.show(closest, marker.node());
            tip.style("pointer-events","none");
        } else {
            tip.hide();
        }
    };

    var onMouseDown = function() {
        if (!highlightedPath) {
            //don't have a highlightpath so make one
            var e = d3.select(this);
            var elem = e.node();
            var mousePos = d3.mouse(elem);
            var x = mousePos[0];
            var y = mousePos[1];

            var paths = seriesContainer.selectAll("path");
            var closest = commonFunctions.closestPath(x, y, paths);

            var closestPath = closest.closestPath;
            var closestPos = closest.closestPos;
            var closestKey = closest.closestKey;

            if (closest && closest.closestPath){
                highlightedPath = closestPath;
                setMarkerPosition(closest);
            }
        } else {
            //had a highlight path so now clear it
            highlightedPath = null;
        }
        updateHighlight(seriesContainer);
        toggleMarkerVisibility(closest);
    };

    var onMouseMove = function() {
        var e = d3.select(this);
        var elem = e.node();
        var mousePos = d3.mouse(elem);
        var x = mousePos[0];
        var y = mousePos[1];

        if (highlightedPath ) {
            var closest = commonFunctions.closestPath(x, y, highlightedPath);

            var closestPath = closest.closestPath;
            var closestPos = closest.closestPos;
            var closestKey = closest.closestKey;

            //console.log(closestPos.x + "," + closestPos.y);

            if (closestPos) {
                setMarkerPosition(closest);
            }
        }
    };

    this.getInstance = function(containerNode) {
        return new visualisations.LineChart(containerNode);
    };

    var defaultOpenSessionText = "IN";
    var defaultCloseSessionText = "OUT";
    var openSessionText = null;
    var closeSessionText = null;
    var stateCounting = false;

    //Sessionise the data to reduce the point events into a number of sessions,
    //each with a start and end time.  Sessionisation is based on events being within
    //a certain time of each other to be included in the same session
    var sessioniseData = function(data, settings) {

        var sessionThresholdMillis;
        if (settings && settings.thresholdMs && typeof(parseInt(settings.thresholdMs, 10) == "number")) {
            sessionThresholdMillis = parseInt(settings.thresholdMs);
        } else {
            sessionThresholdMillis = 0;
        }

        openSessionText = settings.openSessionText || defaultOpenSessionText;
        closeSessionText = settings.closeSessionText || defaultCloseSessionText;

        //loop through the series
        data.values.forEach(function(singleSeriesData) {
            //console.log("########Series: " + data.values[j].key);

            //Need to ensure data is ordered by the other axis so that
            //we can do state counting
            singleSeriesData.values.sort(function(a, b) {
                return a[0] - b[0];
            });
            var count = 0;

            //loop through all the values for the series
            singleSeriesData.values.forEach(function(pointData) {
                var stateChange = pointData[1];

                if (stateChange == openSessionText) {
                    count++;
                } else if (stateChange == closeSessionText) {
                    count--;
                }
                pointData[1] = count;
            });
        });
    };



    this.setData = function(context, settings, data) {

        if (data && data != null) {
            // If the context already has a colour set then use it, otherwise set it
            // to use this one.  
            if (context) {
                if (context.color) {
                    colour = context.color;
                } 
            }

            if (settings) {
                stateCounting = false;
                if (settings.stateCounting && settings.stateCounting.toLowerCase() == "true"){
                    stateCounting = true;
                }

                if (stateCounting) {
                    data.types[1] = "NUMBER";
                    data.values.forEach(function(cellData) {
                        sessioniseData (cellData, settings);
                    });

                    commonFunctions.dataAggregator()
                        .setRecursive(true)
                        .setUseVisibleValues(false)
                        .aggregate(data);
                }

                if (settings.interpolationMode) {
                    interpolationMode = settings.interpolationMode;
                }

                if (grid == undefined){
                    //initialise the grid
                    grid = new visualisations.GenericGrid(element);
                }

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
                    //commonFunctions.setColourDomain(colour, data, 0, "SYNCHED_SERIES");
                    context.color = colour;
                } else {
                    //ensure there is no colour scale in the context so each grid cel vis can define its own
                    delete context.color;
                }
                grid.buildGrid(context, settings, data, this, commonConstants.transitionDuration, synchedFields);
            }
        }
    };

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
        }


        var mode = (settings.synchSeries && settings.synchSeries.toLowerCase() == "true") ? "SYNCHED_SERIES" : "SERIES";
        if (typeof(context) === "undefined" || typeof(context.color) === "undefined") {
            commonFunctions.setColourDomain(colour, data, 0, mode);
        }

        visData = data;
        update();
    };

    var update = function(duration) {
        if (visData) {
            var visibleValues = visData.visibleValues();
            //console.log('update called series: ' + visData.key + ' valueCount:' + visData.values.length);

            //clear any existing hover tips
            highlightedPath = null;
            updateHighlight(seriesContainer);
            toggleMarkerVisibility(null);

            width = commonFunctions.gridAwareWidthFunc(true, containerNode, element, margins);
            height = commonFunctions.gridAwareHeightFunc(true, containerNode, element, margins);
            fullWidth = commonFunctions.gridAwareWidthFunc(false, containerNode, element, margins);
            fullHeight = commonFunctions.gridAwareHeightFunc(false, containerNode, element, margins);

            canvas
                .attr("width", fullWidth)
                .attr("height", fullHeight);

            mouseEventRect
                .attr("width",width + "px")
                .attr("height",height + "px");

            svg.attr("transform", "translate(" + margins.left + "," + margins.top + ")");
            xAxisContainer.attr("transform", "translate(0," + height + ")");

            //Construct the two axes
            xSettings = commonFunctions.createAxis(visData.types[0], 0, width);
            xScale = xSettings.scale;
            xSettings.setRangeDomain(visData.types[0], visData, 0);
            commonFunctions.buildAxis(xAxisContainer, xSettings, "bottom", null, 70, visSettings.displayXAxis);

            ySettings = commonFunctions.createAxis(visData.types[1], height, 0);
            yScale = ySettings.scale;
            ySettings.setRangeDomain(visData.types[1], visData, 1);
            commonFunctions.buildAxis(yAxisContainer, ySettings, "left", null, 70, visSettings.displayYAxis);

            if (commonFunctions.resizeMargins(margins, xAxisContainer, yAxisContainer) == true) {
                update();
            } else {
                seriesContainer.call(tip);

                var g = seriesContainer
                    .selectAll("g")
                    .data(visibleValues, function(d) {
                        return d.key;
                    });

                var series = g.enter()
                    .append("svg:g")
                    .style("pointer-events", "none");

                g.exit()
                    .transition()
                    .attr("opacity", "0")
                    .remove();

                series.append("svg:path")
                    .style("stroke-width", "1.5px")
                    .style("pointer-events", "none");

                // A line generator for the series.
                var line = d3.svg.line()
                    .interpolate(interpolationMode)
                    .x(function(d) {
                        //  console.log("x is " + d[0] + " scaled to " + xScale(d[0]));
                        return xScale(d[0]);
                    })
                    .y(function(d) {
                        //	console.log("y is " + d[1] + " scaled to " + yScale(d[1]));
                        return yScale(d[1]);
                    });

                var colourFunc = function(d) {
                    return colour(d.key);
                };

                g.each(function(d) {
                    var e = d3.select(this);

                    e.select("path")
                        .style("stroke", colourFunc)
                        .attr("class", commonFunctions.makeColouredElementClassStringFunc(function(d) {
                            return d.key;
                        }))
                    .transition()
                        .attr("d", function(d) {
                            var path = line(d.values);
                            return path;
                        });
                });
            }
        }
    };

    this.resize = function() {
        commonFunctions.resize(grid, update, element, margins, width, height);
    }; 

    this.teardown = function() {
        if (typeof(tip) != "undefined"){
            tip.destroy();
        }
    };

    this.getColourScale = function(){
        return colour;
    };
    
    //The position in the vaules array that will be used for the legend key, null
    //means it is series based
    this.getLegendKeyField = function() {
        return null;
    };
};
