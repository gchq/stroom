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
/*
 * Visulisation to display a day/hour map of point events for multiple series.
 * It has the following dimensions:
 * x -           the hour of the day
 * y -           the day
 * cell colour - the value for that hour
 * 
 * Data is expected in the form of two dimensions per series, an exact millisecond time value (values[0]) and the value at that time (values[1]), which may 
 * just be one for events with no value.  
 * 
 */
if (!visualisations) {
    var visualisations = {};
}
visualisations.HourDayPointMap = function(containerNode) {
    var element = window.document.createElement("div");
    this.element = element;

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
    var color = commonConstants.categoryGoogle();
    var width;
    var height;

    //var xSettings;
    var xSettingsTime;
    var ySettingsOrdinal;
    var ySeriesSubSettings;
    var xScaleTime;
    var yScaleOrdinal;
    var xAxis;
    var yAxis;
    var yAxisData = [];
    var gridSizeX;
    var gridSizeY;

    var canvas ;

    var svg ;
    var tip;
    var inverseHighlight;

    // Add the series data.
    var seriesContainer ;

    // Add the x-axis.
    var xAxisContainer ;

    // Add the y-axis.
    var yAxisContainer ;
    var yAxisSwimLanesContainer;

    var visData;

    //one off initialisation of all the local variables, including
    //appending various static dom elements
    var initialise = function() {
        initialised = true;

        width = commonFunctions.gridAwareWidthFunc(true, containerNode, element, margins);
        height = commonFunctions.gridAwareHeightFunc(true, containerNode, element, margins);

        canvas = d3.select(element).append("svg:svg");

        svg = canvas.append("svg:g");

        if (typeof(tip) == "undefined") {
            inverseHighlight = commonFunctions.inverseHighlight();
            tip = inverseHighlight.tip()
                .html(function(tipData) { 
                    var highlightedCircleDate = tipData.values[0];
                    var highlightedCircleValue = tipData.values[1];
                    var dateTxt = new Date(highlightedCircleDate);
                    var html = inverseHighlight.htmlBuilder()
                        .addTipEntry("Name",commonFunctions.autoFormat(tipData.key, visSettings.seriesDateFormat))
                        .addTipEntry("Date",commonFunctions.autoFormat(dateTxt))
                        .addTipEntry("Value",commonFunctions.autoFormat(highlightedCircleValue))
                        .build();
                    return html;
                });

            //optionally call methods on tip such as .direction, .offset, .html to customise it
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

        yAxisSwimLanesContainer = svg.append("svg:g")
            .attr("class", "vis-swimLane" + " yAxis");
    }

    var circleRadiusFunc = function(gridSizeX) {
        return Math.round(gridSizeX * 0.07);
    }

    var highlightedStrokeWidthFunc = function(gridSizeX) {
        return Math.max(1, Math.round(gridSizeX * 0.04)) + "px";
    }

    var fillFunc = function(d) { 
        return color(d.key);
    }	

    var dataPointKeyFunc = function(d) {
        //create a key for the dataItem such that it returns the mills of the hour since epoch
        return d[0] + (d[1] * commonConstants.millisInHour);
    }

    //var setColourDomain = function(scale, data, i, mode) {
        ////console.log("Setting domain - " + data.key + " - " + color.domain());
        //var domain;

        //if (mode == "VALUE" && data.types[i] == "GENERAL") {
            //domain = data.unique[i];
        //} else if (data.uniqueKeys && mode == "SYNCHED_SERIES") {
            //domain = data.uniqueKeys;
        //} else {
            //domain = data.values.map(function(d) {
                //return d.key;
            //});
        //}
        //scale.domain([]);
        //scale.domain(domain);
        ////console.log("Setting domain - " + data.key + " - " + color.domain());
    //};

    //Method to allow the grid to call back in to get new instances for each cell
    this.getInstance = function(containerNode) {
        return new visualisations.HourDayPointMap(containerNode);
    }

    //Public method for setting the data on the visualisation(s) as a whole
    //This is the entry point from Stroom
    this.setData = function(context, settings, data) {

        if (data && data !==null) {
            // If the context already has a colour set then use it, otherwise set it
            // to use this one.
            if (context) {
                if (context.color) {
                    color = context.color;
                } else {
                    //context.color = color;
                }
            }

            //#########################################################
            //Perform any visualisation specific data manipulation here
            //#########################################################

            if (settings) {
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
                    //setColourDomain(color, data, 0, "SYNCHED_SERIES");
                    context.color = color;
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


    var processData = function(data) {

        //console.log("period start: " + new Date(data.min[0]) + " end: " + new Date(data.max[0]));

        /*
         * This is what we are trying to build
         * 
         * 0=x (hour), 1=y (day), 2=z (cell value), 3=y as a string
         * 
         * min
         * --0,1,2
         * max
         * --0,1,2
         * types
         * --0,1,2
         * values
         * --0
         * ----min
         * ------0,1,2
         * ----max
         * ------0,1,2
         * ----values
         * ------0
         * --------0,1,2,3
         * ------1
         * --------0,1,2,3
         */

        //define the types

        data.types[3] = "TEXT";
        data.types[4] = "DATE_TIME";
        data.types[5] = "NUMBER";

        //work out the new min and max values for the top level		
        data.min[4] = commonFunctions.truncateToStartOfDay(data.min[0]);
        data.max[5] = commonFunctions.millisSinceStartOfDay(data.min[0]);

        data.max[4] = commonFunctions.truncateToStartOfDay(data.max[0]);
        data.max[5] = commonFunctions.millisSinceStartOfDay(data.max[0]);

        //add all the truncated day values for our data into an array that will be bound to the y axis.
        yAxisData = [];
        var timeMs = commonFunctions.truncateToStartOfDay(data.min[0]);
        while (timeMs <= commonFunctions.truncateToStartOfDay(data.max[0])){
            var dateStr = new Date(timeMs).toDateString();
            yAxisData.push(dateStr);

            timeMs += commonConstants.millisInDay;
        }

        //var seriesCount = data.values.length;
        var visibleValues = data.visibleValues();
        //console.log("Series count: " + seriesCount);

        //for (var j = 0; j < seriesCount; j++){
        visibleValues.forEach(function(singleSeriesData) {

            //console.log("Series: " + data.values[j].key);

            //var arrLen = data.values[j].values.length;

            //for (var i = 0; i < arrLen; i++){
            singleSeriesData.values.forEach(function(pointData) {

                //var hourOfDay = commonFunctions.getTwoDigitHourOfDay(data.values[j].values[i][0]);
                var dayMs = commonFunctions.truncateToStartOfDay(pointData[0]);

                //console.log("time: " + eventDate + " val: " + cellValue + " hour: " + hourOfDay + " day: " + new Date(dayMs));

                //values[i][0] = event time ms
                //values[i][1] = event value
                //values[i][2] = hour of day
                //values[i][3] = day as a string (e.g. 28 May 2015)
                //values[i][4] = day as millis
                //values[i][5] = millis since start of day

                //data.values[j].values[i][2] = hourOfDay;
                pointData[3] = new Date(dayMs).toDateString();
                pointData[4] = dayMs;
                pointData[5] = commonFunctions.millisSinceStartOfDay(pointData[0]);

                //console.log("hourOfDay: " + hourOfDay + " day: " + new Date(dayMs) + " val: " + data.values[j].values[i][1]);
            });
        });
        return data;
    }

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
                color = context.color;
            }
        }

        if (settings){
            visSettings = settings;
        }

        var mode = (settings.synchSeries && settings.synchSeries.toLowerCase() == "true") ? "SYNCHED_SERIES" : "SERIES";
        if (typeof(context) === "undefined" || typeof(context.color) === "undefined") {
            commonFunctions.setColourDomain(color, data, 0, mode);
        }

        visData = processData(data);
        update(commonConstants.transitionDuration);
    };

    var update = function(duration) {
        if (visData) {
            var visibleValues = visData.visibleValues();

            width = commonFunctions.gridAwareWidthFunc(true, containerNode, element, margins);
            height = commonFunctions.gridAwareHeightFunc(true, containerNode, element, margins);
            fullWidth = commonFunctions.gridAwareWidthFunc(false, containerNode, element, margins);
            fullHeight = commonFunctions.gridAwareHeightFunc(false, containerNode, element, margins);

            var daysOnChart = yAxisData.length;
            var seriesCount = visibleValues.length;

            //always 24 hours in a day
            gridSizeX = Math.floor(width / 24);
            //work out the number of days in the data and add one so we have a cell for the max entry
            gridSizeY = Math.min(gridSizeX * 1.5, Math.floor(height / daysOnChart));

            //padding between the outer most point and a swimlane
            var gridYPadding = Math.round(gridSizeY * 0.1);

            //the height of space we allow points to ocupy in the swimlane
            var usableGridSizeY =  gridSizeY - (gridYPadding * 2);

            //the height of a notional grid square in the usable part of the swimlane 
            var subGridSizeY = usableGridSizeY / seriesCount;

            canvas.attr("width", fullWidth).attr("height",
                fullHeight);
            svg.attr("transform", "translate(" + margins.left + "," + margins.top + ")");
            xAxisContainer.attr("transform", "translate(0," + height + ")");

            //Alter the number of xAxis ticks based on the axis length so they are not too squashed
            var xTicks = commonFunctions.getHourAxisTickMarkCount(width);

            xSettingsTime = commonFunctions.createAxis(visData.types[5], 0, width);
            xScaleTime = xSettingsTime.scale;
            xSettingsTime.setExplicitDomain([0,24]);
            commonFunctions.buildAxis(xAxisContainer, xSettingsTime, "bottom", xTicks, null, visSettings.displayXAxis);

            ySettingsOrdinal = commonFunctions.createAxis(visData.types[3], height, 0);
            yScaleOrdinal = ySettingsOrdinal.scale;
            ySettingsOrdinal.setExplicitDomain(yAxisData);
            commonFunctions.buildAxis(yAxisContainer, ySettingsOrdinal, "left", null, null, visSettings.displayYAxis);

            ySeriesSubSettings = commonFunctions.createAxis("ORDINAL", 0, usableGridSizeY);
            ySeriesSubScale = ySeriesSubSettings.scale;

            //xAxis = xSettingsTime.axis.orient("bottom").ticks(xTicks);
            //yAxis = ySettingsOrdinal.axis.orient("left");

            //just give it a min and max value so it has the limits of the range and can then be used to scale values correctly

            //xAxisContainer.transition()
            //.duration(commonConstants.transitionDuration)
            //.call(xAxis.orient("bottom"));

            //call the y axis and make the weekday labels bold
            yAxisContainer.transition()
                .duration(commonConstants.transitionDuration)
            //.call(yAxis.orient("left"))
                .selectAll("text")
                .attr("class", function(d) {
                    if (d.indexOf("Sun") == 0 || d.indexOf("Sat") == 0){
                        //return staticClasses + " weekend";
                        return  "weekend";
                    } else {
                        return "";	
                    }
                });

            if (commonFunctions.resizeMargins(margins, xAxisContainer, yAxisContainer) == true) {
                update();
            } else {
                seriesContainer.call(tip);

                var seriesData = visibleValues.map(function (d) {
                    return d.key;
                });
                //console.log(seriesData);
                ySeriesSubSettings.setExplicitDomain(seriesData);

                //cache of keys to hash values
                var keyToHashedKeyMap = {};

                var g = seriesContainer
                    .selectAll("g")
                    .data(visibleValues, function(d) {
                        return d.key;
                    });

                var series = g.enter()
                    .append("svg:g")
                    .attr("id", function(d) { 
                        return d.key; 
                    });

                g.exit().transition()
                    .duration(commonConstants.transitionDuration)
                    .attr("opacity", "0")
                    .remove();

                g.each(function(seriesData, i) {
                    var seriesGroup = d3.select(this);

                    //use a key function that returns the millis since epoch of each cell so the transitions work correctly
                    var series = seriesGroup.selectAll("circle")
                        .data(seriesData.values, function(d) { 
                            return seriesData.key + "~~~" + (d[5] / commonConstants.millisInHour); 
                        });

                    var fillColour = fillFunc(seriesData);

                    var seriesYOffset = Math.round(gridYPadding + ySeriesSubScale(seriesData.key) + (subGridSizeY / 2));

                    //console.log("Series i: " + i); 
                    //console.log("Series: " + d.key + " gridSizeY: " + gridSizeY + " offset: " + seriesYOffset + " padding: " + gridYPadding + " usableGridSizeY: " + usableGridSizeY);

                    var legendKeyFunc = function(d, i){
                        return seriesData.key;
                    };

                    series.enter()
                        .append("svg:circle")
                        .attr("class", commonFunctions.makeColouredElementClassStringFunc(legendKeyFunc, "myHoverableElement", keyToHashedKeyMap))
                        .style("fill-opacity", 1e-6)
                        .attr("opacity", 1e-6);

                    series.exit().transition()
                        .duration(commonConstants.transitionDuration)
                        .attr("opacity", "0")
                        .remove();

                    series.each(function(dataPoint) {

                        var graphPoint = d3.select(this);

                        //the the two digit hour
                        var x = xScaleTime(dataPoint[5] / commonConstants.millisInHour);

                        //use the string version of the day axis
                        var y = yScaleOrdinal(dataPoint[3]) + seriesYOffset;

                        //					if (isNaN(x) || isNaN(y)) {
                        //						dumpPoint(dataPoint);
                        //					}

                        graphPoint.transition()
                            .duration(commonConstants.transitionDuration)
                            .attr("opacity", "1")
                            .attr("cx", x)
                            .attr("cy", y)
                            .attr("r", circleRadiusFunc(gridSizeX))
                            .style("stroke-opacity", 1)
                            .style("fill", fillColour)
                            .style("fill-opacity", 0.6)
                            .style("stroke", fillColour)
                            .style("stroke-width", "1.5px");
                    });

                    var cssSelector = ".myHoverableElement";
                    commonFunctions.addDelegateEvent(
                        seriesGroup, 
                        "mouseover",
                        cssSelector, 
                        inverseHighlight.makeInverseHighlightMouseOverHandler(seriesData.key, visData.types, seriesContainer, cssSelector));

                    commonFunctions.addDelegateEvent(
                        seriesGroup,
                        "mouseout",
                        cssSelector,
                        inverseHighlight.makeInverseHighlightMouseOutHandler(seriesContainer, cssSelector));
                });

                //construct the swim lanes

                var swimlanes = yAxisSwimLanesContainer.selectAll("line").data(yAxisData, function (d) {return d;} );

                swimlanes.enter().append("svg:line")
                    .attr("class", "separatorLine")
                    .style("stroke", "#aaaaaa");

                swimlanes.exit().transition()
                    .duration(commonConstants.transitionDuration)
                    .attr("opacity", "0")
                    .remove();

                swimlanes.each(function (val) {
                    var swimLane = d3.select(this);

                    var y = yScaleOrdinal(val)

                    swimLane.transition()
                        .duration(commonConstants.transitionDuration)
                        .attr("x1", 0)
                        .attr("y1", y)
                        .attr("x2", width)
                        .attr("y2", y)
                        .attr("opacity", "1")
                        .style("stroke-opacity", 1);
                });
            }
            ////The width/height of the axis text can vary so re-calculate the sizes then call update again
            //var xAxisBox = xAxisContainer.node().getBBox();
            //var yAxisBox = yAxisContainer.node().getBBox();
            //var xAxisHeight = Math.max(20, xAxisBox.height + 2);
            //var yAxisWidth = Math.max(20, yAxisBox.width + 2);
            //if (m[2] != xAxisHeight || m[3] != yAxisWidth) {
            //m[2] = xAxisHeight;
            //m[3] = yAxisWidth;
            //update(0);
            //}
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
        return color;
    };

    function dumpPoint(d){
        console.log("hourOfDay: " + d[0] + " dayMs: " + d[1] + " day: " + new Date(d[1]) + " val: " + d[2]);
    }
}
