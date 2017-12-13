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

visualisations.StackedArea = function(containerNode) {

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
    var colour = commonConstants.categoryGoogle();
    var width;
    var height;

    var interpolationMode = "step";

    var canvas;
    var svg;
    //var tip;
    var markerTip;
    var highlightedPath;
    var mouseOverPath;

    var xScale;
    var yScale;

    var xAxis;
    var yAxis;

    var interpolateArr = [ 
        "linear",
        "linear-closed",
        "step",
        "step-before",
        "step-after",
        "basis",
        "basis-open",
        "basis-closed",
        "bundle",
        "cardinal",
        "cardinal-open",
        "cardinal-closed",
        "monotone" 
    ];

    var interLoop = 0;

    var selectedSeries;

    // Add the series data.
    var seriesContainer ;

    // Add the x-axis.
    var xAxisContainer ;

    // Add the y-axis.
    var yAxisContainer ;

    // Add a marker to show a point on a line nearest to the mouse.
    var marker ;

    var stack;
    var nest; 
    var stackedArea; 
    var flatArea; 

    var visData;

    var area;

    var initialise = function() {
        initialised = true;

        width = commonFunctions.gridAwareWidthFunc(true, containerNode, element);
        height = commonFunctions.gridAwareHeightFunc(true, containerNode, element);

        canvas = d3.select(element)
            .append("svg:svg");

        svg = canvas.append("svg:g");

        if (typeof(markerTip) == "undefined") {
            inverseHighlightMarker = commonFunctions.inverseHighlight();
            markerTip = inverseHighlightMarker.tip()
                .html(function(tipData) { 
                    var html = inverseHighlightMarker.htmlBuilder()
                        .addTipEntry("Name",commonFunctions.autoFormat(tipData.closestKey, visSettings.seriesDateFormat))
                        .addTipEntry("X",commonFunctions.autoFormat(xScale.invert(tipData.closestPos.x)))
                        .addTipEntry("Y",commonFunctions.autoFormat(yScale.invert(tipData.closestPos.y)))
                        .build();
                    return html;
                });
        }

        // Add the series data.
        seriesContainer = svg.append("svg:g")
            .attr("class", "vis-series");

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

        stack = d3.layout.stack()
            .offset("zero")
            .values(function (d) { return d.values; })                      
            .values(function (d) { return d.values; })                      
            .x(function (d) {
                if (d !== null) {
                    return d.key;
                } else {
                    return 0;
                }
            })
            .y(function (d) {
                if (d !== null) {
                    return d.value;
                } else {
                    return 0;
                }
            });
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
            markerTip.show(closest, marker.node());
            markerTip.style("pointer-events","none");
        } else {
            marker
                .attr("opacity", "0");
            markerTip.hide();
        }
    };

    var setMarkerPosition = function(closest) {
        if (closest && closest.closestPos){
            marker
                .attr("transform", "translate(" + closest.closestPos.x + "," + closest.closestPos.y + ")");
            markerTip.show(closest, marker.node());
            markerTip.style("pointer-events","none");
        } else {
            markerTip.hide();
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

            var closest = commonFunctions.closestPath(x, y, e);

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
            //TODO - Need to consider what the tip should show.  I.e. if the interpolation is
            //basis then do we show the interpolated value or jump between the actual data points.
            //Also the y axis value is currently of little value in its stacked form as it doesn't represent
            //anything meaningful (it shows d.y+d.y0).  Instead we need to show the thickness of stacked area
            //i.e. d.y.
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
        return new visualisations.StackedArea(containerNode);
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

            //find all the unique nesting keys so we can synch series if needs be
            //commonFunctions.computeUniqueKeys(data);

            if (settings) {
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

                if (settings.synchSeries && settings.synchSeries.toLowerCase() == "true") {
                    //series are synched so setup the colour scale domain and add it to the context
                    //so it can be passed to each grid cell vis
                    //commonFunctions.setColourDomain(color, data, 0, "SYNCHED_SERIES");
                    context.color = colour;
                } else {
                    //ensure there is no colour scale in the context so each grid cel vis can define its own
                    delete context.color;
                }
                //Get grid to construct the grid cells and for each one call back into a 
                //new instance of this to build the visualisation in the cell
                //The last array arg allows you to synchronise the scales of fields
                grid.buildGrid(context, settings, data, this, 0, synchedFields);
            }
        }
    };

    //Public entry point for the Grid to call back in to set the cell level data on the cell level 
    //visualisation instance. Data will only contain the branch of the tree for this cell
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

        if (settings) {
            visSettings = settings;
            if (settings.interpolationMode) {
                interpolationMode = settings.interpolationMode;
                area.interpolate(interpolationMode);
            }
        }

        var mode = (settings.synchSeries && settings.synchSeries.toLowerCase() == "true") ? "SYNCHED_SERIES" : "SERIES";
        if (typeof(context) === "undefined" || typeof(context.color) === "undefined") {
            commonFunctions.setColourDomain(colour, data, 0, mode);
        }

        var matrix = pack(data);

        stack(matrix);
        data.stackData = matrix;
        visData = data;
        update(commonConstants.duration);
    };

    var pack = function(d) {
        var visibleValues = d.visibleValues();
        var allX = d3.map([], function (d) {return d;});
        var i, j;

        for (i = 0; i < visibleValues.length; i++) {
            for (j = 0; j < visibleValues[i].values.length; j++) {
                if (allX.has(visibleValues[i].values[j][0]) === false) {
                    allX.set(visibleValues[i].values[j][0],0);
                }
            }
        }

        var res = [];
        var allXKey = function (d) { return d;};
        var baseMapSort = function (a,b) {
            return a.key < b.key ? -1 : a.key > b.key ? 1 : b.key >= a.key ? 0 : 0;
        };

        for (i = 0; i < visibleValues.length; i++) {
            var baseMap = d3.map(allX, allXKey);
            for (j = 0; j < visibleValues[i].values.length; j++) {
                baseMap.set(visibleValues[i].values[j][0], visibleValues[i].values[j][1]);
            }
            var series = {
                key: visibleValues[i].key, 
                min: visibleValues[i].min, 
                max: visibleValues[i].max, 
                sum: visibleValues[i].sum, 
                values: baseMap.entries().sort(baseMapSort) 
            };
            res.push(series);
        }
        return res.sort(function (a,b) {
            return a.max[1] < b.max[1] ? 1 : a.max[1] > b.max[1] ? -1 : a.max[1] >= b.max[1] ? 0 : 0;
        });
    };

    var update = function(duration) {
        if (visData) {
            var visibleValues = visData.visibleValues();

            width = commonFunctions.gridAwareWidthFunc(true, containerNode, element, margins);
            height = commonFunctions.gridAwareHeightFunc(true, containerNode, element, margins);
            fullWidth = commonFunctions.gridAwareWidthFunc(false, containerNode, element, margins);
            fullHeight = commonFunctions.gridAwareHeightFunc(false, containerNode, element, margins);

            canvas
                .attr("width", fullWidth)
                .attr("height", fullHeight);

            svg.attr("transform", "translate(" + margins.left + "," + margins.top + ")");
            xAxisContainer.attr("transform", "translate(0," + height + ")");

            xSettings = commonFunctions.createAxis(visData.types[0], 0, width);
            xScale = xSettings.scale;
            xSettings.setRangeDomain(visData.types[0], visData, 0);
            commonFunctions.buildAxis(xAxisContainer, xSettings, "bottom", null, null, visSettings.displayXAxis);

            //TODO because we are using the stack data for our y axis limit, it means
            //synching y axis wont work as the grid doesn't have access to this data
            var yScaleMaxVal = d3.max(visData.stackData, function (d) {
                return d3.max(d.values, function (d) {
                    return d.y0 + d.y;
                });
            });

            ySettings = commonFunctions.createAxis(visData.types[1], height, 0);
            yScale = ySettings.scale;
            ySettings.setExplicitDomain([0, yScaleMaxVal]);
            commonFunctions.buildAxis(yAxisContainer, ySettings, "left", null, null, visSettings.displayYAxis);

            if (commonFunctions.resizeMargins(margins, xAxisContainer, yAxisContainer) == true) {
                update();
            } else {
                nest = d3.nest()
                    .key(function(d) {return d.key;});

                stackedArea = d3.svg.area()
                    .interpolate("basis")
                    .x(function(d) { 
                        return xScale(d.key); 
                    })
                    .y0(function(d) { return yScale(d.y0); })
                    .y1(function(d) { return yScale(d.y0+d.y); });

                flatArea = d3.svg.area()
                    .x(function(d) { return xScale(d.key); })
                    .y0(function(d) { return yScale(0); })
                    .y1(function(d) { return yScale(d.value); });

                seriesContainer.call(markerTip);

                var g = seriesContainer.selectAll("path.vis-coloured-element")
                    .data(visData.stackData, function(d) {
                        //console.log("d.key: " + d.key);
                        return d.key;
                    });

                area = stackedArea;

                var fillFunc = function(d) {
                    return colour(d.key);
                };

                var legendKeyFunc = function(d) {
                    return d.key;
                };

                g.enter()
                    .append("svg:path")
                    .attr("opacity","0")
                    .style("fill-opacity", 1)
                    .style("pointer-events", "all")
                    .style("cursor", "crosshair");

                g
                    .attr("class", commonFunctions.makeColouredElementClassStringFunc(legendKeyFunc))
                    .attr("id",function (d) { 
                        return d.key;
                    })
                    .style("fill", fillFunc)
                    .attr("d", function(d) {
                        var dVal = area(d.values);
                        if (dVal.indexOf("NaN") > -1) {
                            console.log("NaN dVal: " + dVal);
                        }
                        return area(d.values);
                    })
                    .transition()
                    .duration(duration)
                    .attr("opacity", "1");

                g.exit()
                    .remove();

                commonFunctions.addDelegateEvent(svg, "mousemove", "path", onMouseMove);
                commonFunctions.addDelegateEvent(svg, "mousedown", "path", onMouseDown);
            }
        }
    };


    this.resize = function() {
        commonFunctions.resize(grid, update, element, margins, width, height);
    }; 

    this.teardown = function() {
        if (typeof(markerTip) != "undefined"){
            markerTip.destroy();
        }
    };

    this.getColourScale = function(){
        return colour;
    };

    //Legend key is based on series so null key field
    this.getLegendKeyField = function() {
        return null;
    };
};



