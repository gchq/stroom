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

//IIFE to prvide shared scope for sharing state and constants between the controller 
//object and each grid cell object instance
(function(){

    var fillFuncMaker;
    var nameFuncMaker;
    var maxSeriesPerGridCell;

    visualisations.BarChart = function(containerNode) {

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
        var axisLabelTip;
        var inverseHighlight;

        var seriesContainer;
        var xAxisContainer;
        var yAxisContainer;
        var marker;
        var label;
        var labelName;
        var labelX;
        var labelY;
        var bucketSizeMs;
        var xAxisTruncFunc;
        var legendKeyField = 0;

        //one off initialisation of all the local variables, including
        //appending various static dom elements
        var initialise = function() {
            initialised = true;

            //d3.select(element).append("style").text(style);

            width = commonFunctions.gridAwareWidthFunc(true, containerNode, element);
            height = commonFunctions.gridAwareHeightFunc(true, containerNode, element);

            canvas = d3.select(element)
                .append("svg:svg");

            svg = canvas.append("svg:g");

            //Set up the bar highlighting and hover tip
            if (typeof(tip) == "undefined") {
                inverseHighlight = commonFunctions.inverseHighlight();
                tip = inverseHighlight.tip()
                    .html(function(tipData) { 
                        var html = inverseHighlight.htmlBuilder()
                            .addTipEntry("Name",commonFunctions.autoFormat(tipData.key, visSettings.seriesDateFormat))
                            .addTipEntry("X",commonFunctions.autoFormat(xSettings.getValue(tipData.values[0])))
                            .addTipEntry("Y",commonFunctions.autoFormat(ySettings.getValue(tipData.values[1])))
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
        }

        //Method to allow the grid to call back in to get new instances for each cell
        this.getInstance = function(containerNode) {
            return new visualisations.BarChart(containerNode);
        }

        //if we have one series then colour by value (if GENERAL) otherwise
        //colour by series
        var makeValueFillFunc = function(key, colour) {
            return function(d, i) {
                //console.log("ValueFill - colour for key: " + key + ", d: " + d + ", i" + i + ", is " + colour(d[legendKeyField]) + " colour.domain(): [" + colour.domain() + "]");
                return colour(d[legendKeyField]);
            };
        }

        var makeSeriesFillFunc = function(key, colour) {
            return function(d, i) {
                //console.log("SeriesFill - colour for key: " + key + ", d: " + d + ", i" + i + ", is " + colour(key) + " colour.domain(): [" + colour.domain() + "]");
                return colour(key);
            };
        };

        var makeValueNameFunc = function(key) {
            return function(d, i) {
                return d[legendKeyField];
            };
        }

        var makeSeriesNameFunc = function(key) {
            //console.log("key: " + key + " colour: " + colour(key) + " domain: " + colour.domain());
            return function(d, i) {
                return key;
            };
        };

        var visData;
        var visSharedState;

        //Public method for setting the data on the visualisation(s) as a whole
        //This is the entry point from Stroom
        this.setData = function(context, settings, data) {

            if (data && data !==null) {
                // If the context already has a colour set then use it, otherwise set it
                // to use this one.
                if (context) {
                    if (context.color) {
                        colour = context.color;
                    } 
                }

                //#########################################################
                //Perform any visualisation specific data manipulation here
                //#########################################################

                if (settings) {
                    //Inspect settings to determine which axes to synch, if any.
                    //Change the settings property(s) used according to the vis
                    var synchedFields = [];
                    if (commonFunctions.isTrue(settings.synchXAxis)) {
                        synchedFields.push(0);
                    }
                    if (commonFunctions.isTrue(settings.synchYAxis)) {
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

                    if (data.values) {
                        maxSeriesPerGridCell = d3.max(data.values, function(gridCellData) {
                            return gridCellData.values.length;
                        });
                        if (maxSeriesPerGridCell === 1 && 
                            (data.types[0] == "GENERAL" || data.types[0] == "TEXT") && 
                            !commonFunctions.isTrue(settings.synchSeries)
                        ) {
                            legendKeyField = 0;
                            fillFuncMaker = makeValueFillFunc;
                            nameFuncMaker = makeValueNameFunc;
                        } else {
                            legendKeyField = null;
                            fillFuncMaker = makeSeriesFillFunc;
                            nameFuncMaker = makeSeriesNameFunc;
                        }
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
                if (settings.bucketSize) {
                    if (typeof(settings.bucketSize) == "number") {
                        bucketSizeMs = parseInt(settings.bucketSize);
                    } else {
                        bucketSizeMs = commonFunctions.decodeTimePeriod(settings.bucketSize);
                    }
                }
            }

            //colour scale and fill function depend on whether the series are synched, whether the values
            //are ordinal or not and how many series we have 
            if ((maxSeriesPerGridCell == 1 && (data.types[0] == "GENERAL" || data.types[0] == "TEXT")) && !commonFunctions.isTrue(settings.synchSeries) ){
                if (typeof(context) === "undefined" || typeof(context.color) === "undefined") {
                    commonFunctions.setColourDomain(colour, data, 0, "VALUE");
                }
            } else {
                var mode = commonFunctions.isTrue(settings.synchSeries) ? "SYNCHED_SERIES" : "SERIES";
                if (typeof(context) === "undefined" || typeof(context.color) === "undefined") {
                    commonFunctions.setColourDomain(colour, data, 0, mode);
                }
            }

            visData = data;
            update(0);
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
                //console.log(visData.values[0].values);

                xSettings = commonFunctions.createAxis(visData.types[0], 0, width);
                xScale = xSettings.scale;
                xSettings.setDomain(visData, visibleValues[0].values, 0);
                commonFunctions.buildAxis(xAxisContainer, xSettings, "bottom", null, null, visSettings.displayXAxis);

                ySettings = commonFunctions.createAxis(visData.types[1], height, 0);
                yScale = ySettings.scale;
                ySettings.setRangeDomain(visData.types[1], visData, 1);
                commonFunctions.buildAxis(yAxisContainer, ySettings, "left", null, null, visSettings.displayYAxis);

                if (commonFunctions.resizeMargins(margins, xAxisContainer, yAxisContainer) == true) {
                    update();
                } else {
                    seriesContainer.call(tip);
                    tip.hide();

                    var g = seriesContainer.selectAll("g")
                        .data(visibleValues, function(d) {
                            return d.key;
                        });

                    var series = g.enter()
                        .append("svg:g");

                    g.exit()
                        .transition()
                        .attr("opacity", "0")
                        .remove();

                    var xOffset = 0;

                    if (visData.types[0] == "TEXT" || visData.types[0] == "GENERAL") {
                        if (visibleValues.length > 1) {
                            barWidth = xScale.rangeBand() / visibleValues.length;
                            xOffset = -barWidth;
                        } else {
                            barWidth = xScale.rangeBand();
                            xOffset = -barWidth;
                        }
                    } else {
                        //Time based so determine a bar width from the bucketsize if supplied
                        barWidth = 1;
                        if (bucketSizeMs){
                            var seriesCount = visibleValues.length;
                            var barWidth = (xScale(visData.min[0] + bucketSizeMs) - xScale(visData.min[0])) / seriesCount;
                        } else {
                            var barWidth = 2;
                        }
                    }
                    xOffset = -barWidth;

                    g.each(function(seriesData) {
                        var e = d3.select(this);

                        var series = e.selectAll("rect")
                            .data(seriesData.visibleValues(), function(pointData) {
                                //console.log(seriesData.key + "~#~" + pointData[0]);
                                return seriesData.key + "~#~" + pointData[0];
                            });

                        series.enter()
                            .append("svg:rect")
                            .attr("class", "symbol")
                            .style("fill-opacity", 1e-6);

                        series.exit()
                            .transition()
                            .attr("opacity", "0")
                            .remove();

                        xOffset += barWidth;

                        series.each(function(point) {
                            var bar = d3.select(this);
                            var fillFunc = fillFuncMaker(seriesData.key, colour);
                            var nameFunc = nameFuncMaker(seriesData.key);
                            var x = xScale(point[0]);
                            var y = yScale(point[1]);
                            var w = barWidth;

                            x += xOffset;
                            if (x < 0) {
                                w += x;
                                x = 0;
                            } else if (x + w > width) {
                                w = width - x;
                                if (w < 0) {
                                    w = 0;
                                }
                            }

                            var h = height - y;

                            w = Math.max(0, w);
                            h = Math.max(0, h);

                            if (isNaN(x)) {
                                //dumpPoint(point);
                                console.log("INVALID X DATA - point[0]: " + point[0] + 
                                    " x: " + x + 
                                    " scale.domain(): [" + xScale.domain() + "]" +
                                    " key: " + seriesData.key);
                            }
                            if (isNaN(y)) {
                                //dumpPoint(point);
                                console.log("INVALID Y DATA - point[1]: " + point[1] + 
                                    " y: " + y + 
                                    " scale.domain(): [" + yScale.domain() + "]" +
                                    " key: " + seriesData.key);
                            }
                            bar
                                .attr("class", commonFunctions.makeColouredElementClassStringFunc(nameFunc, "vis-barchart-element"))
                                .style("fill", fillFunc)
                                .transition()
                                .attr("opacity", "1")
                                .attr("x", x)
                                .attr("y", y)
                                .attr("width", w)
                                .attr("height", h)
                                .style("stroke-opacity", 1)
                                .style( "fill-opacity", 1);
                        });

                        commonFunctions.addDelegateEvent(
                            e, 
                            "mouseover", 
                            "rect", 
                            inverseHighlight.makeInverseHighlightMouseOverHandler(seriesData.key, visData.types, seriesContainer, "rect"));

                        commonFunctions.addDelegateEvent(
                            e, 
                            "mouseout", 
                            "rect", 
                            inverseHighlight.makeInverseHighlightMouseOutHandler(seriesContainer, "rect"));
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
            if (typeof(axisLabelTip) != "undefined"){
                axisLabelTip.destroy();
            }
        };

        this.getColourScale = function(){
            return colour;
        };

        //The position in the vaules array that will be used for the legend key
        this.getLegendKeyField = function() {
            return legendKeyField;
        };

    };

}());
