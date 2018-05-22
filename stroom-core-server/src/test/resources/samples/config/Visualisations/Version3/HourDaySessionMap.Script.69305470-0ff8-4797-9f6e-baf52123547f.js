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
 * Visulisation to display a day/hour map of sessionised events for multiple series.
 * It has the following dimensions:
 * x -           the hour of the day
 * y -           the day, and within each day a swimlane for each series
 * cell colour - The series
 * 
 * Data is expected in the form of two dimensions per series, an exact millisecond time value (values[0]) 
 * just be one for events with no value.  
 *
 * The data will be sessionised based on a threshold time period.  E.g. if the threshold is 5mins then any event within 5mins of another is deemed to be 
 * part of the same session and will be linked into one data 'bar'.
 * 
 */
if (!visualisations) {
    var visualisations = {};
}
visualisations.HourDaySessionMap = function(containerNode) {

    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;

    //Constants for the various value array positions
    var POINT_EVENT_MS_IDX = 0;
    var SESSION_START_MS_IDX = 0;
    var SESSION_END_MS_IDX = 1;
    var EVENT_COUNT_IDX = 2;
    var DATE_STR_IDX = 3;
    var START_OF_DAY_MS_IDX = 4;
    var SESSION_START_MS_SINCE_START_OF_DAY_IDX = 5;
    var SESSION_END_MS_SINCE_START_OF_DAY_IDX = 6;
    var SESSION_ID_IDX = 7;
    var SESSION_START_DATE_STR = 8;
    var SESSION_END_DATE_STR = 9;

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

    //ensure we have the correct instance of d3
    var d3 = window.d3;

    //top, right, bottom, left
    var margins = commonConstants.margins();
    var sessionThresholdMillis;
    var defaultSessionThresholdMillis = 1000 * 60 * 15;

    var circleRadiusFunc = function(gridSizeX) {
        return Math.round(gridSizeX * 0.12);
    }

    var highlightedStrokeWidthFunc = function(gridSizeX) {
        return Math.max(1, Math.round(gridSizeX * 0.27)) + "px";
    }

    var rectRadiusFunc = function(gridSizeX) {
        return Math.round(gridSizeX * 0.5);
    }

    // Create a colour set.
    var colour = commonConstants.categoryGoogle();

    var fillFunc = function(d) { 
        return colour(d.key);
    }	

    var width;
    var height;

    var xSettingsTime;
    var ySettingsOrdinal;
    var ySeriesSubSettings;
    var xScaleTime;
    var yScaleOrdinal;
    var ySeriesSubScale;
    var xAxis;
    var yAxis;
    var canvas;
    var svg;
    var tip;
    var inverseHighlight;
    var seriesContainer;
    var xAxisContainer;
    var yAxisContainer;
    var yAxisSwimLanesContainer;
    
    //one off initialisation of all the local variables, including
    //appending various static dom elements
    var initialise = function() {
        initialised = true;

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
                    var seriesName = tipData.key;
                    var startDate = tipData.values[SESSION_START_MS_IDX];
                    var endDate = tipData.values[SESSION_END_MS_IDX];
                    var eventCount = tipData.values[EVENT_COUNT_IDX];
                    var html = inverseHighlight.htmlBuilder()
                        .addTipEntry("Name",commonFunctions.autoFormat(seriesName, visSettings.seriesDateFormat))
                        .addTipEntry("Start",commonFunctions.autoFormat(new Date(startDate)))
                        .addTipEntry("End",commonFunctions.autoFormat(new Date(endDate)))
                        .addTipEntry("Count",commonFunctions.autoFormat(eventCount))
                        .build();
                    return html;
                });
        }

        // Add the series data.
        seriesContainer = svg
            .append("svg:g")
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

    //Method to allow the grid to call back in to get new instances for each cell
    this.getInstance = function(containerNode) {
        return new visualisations.HourDaySessionMap(containerNode);
    }

    var getScale = function(type, min, max) {
        if (type == "DATE_TIME") {
            return d3.time.scale.utc().range([ min, max ]);
        } else if (type == "NUMBER") {
            return d3.scale.linear().range([ min, max ]);
        } else {
            return d3.scale.ordinal().rangeRoundBands([ min, max ], 0);
        }
    }

    var visData;
    var maxVal;
    var yAxisData = [];

    var processSeriesData = function(seriesData, i, parentArray) {
        //work out the new min and max values for the series level		
        //parentArray[i].min[4] = commonFunctions.truncateToStartOfDay(seriesData.min[POINT_EVENT_MS_IDX]);
        //parentArray[i].min[5] = commonFunctions.millisSinceStartOfDay(seriesData.min[POINT_EVENT_MS_IDX]);

        //parentArray[i].max[4] = commonFunctions.truncateToStartOfDay(seriesData.max[POINT_EVENT_MS_IDX]);
        //parentArray[i].max[5] = commonFunctions.millisSinceStartOfDay(seriesData.max[POINT_EVENT_MS_IDX]);

        //overwrite the existing data with the new sessionised data
        parentArray[i].values = sessioniseData(seriesData, sessionThresholdMillis);
    };

    var processGridSeriesData = function(gridSeriesData, i, parentArray) {

        //work out the new min and max values for the top level		
        //gridSeriesData.min[2] = commonFunctions.getTwoDigitHourOfDay(gridSeriesData.min[POINT_EVENT_MS_IDX]);
        //gridSeriesData.min[4] = commonFunctions.truncateToStartOfDay(gridSeriesData.min[POINT_EVENT_MS_IDX]);
        //gridSeriesData.max[5] = commonFunctions.millisSinceStartOfDay(gridSeriesData.min[POINT_EVENT_MS_IDX]);

        //gridSeriesData.max[2] = commonFunctions.getTwoDigitHourOfDay(gridSeriesData.max[POINT_EVENT_MS_IDX]);
        //gridSeriesData.max[4] = commonFunctions.truncateToStartOfDay(gridSeriesData.max[POINT_EVENT_MS_IDX]);
        //gridSeriesData.max[5] = commonFunctions.millisSinceStartOfDay(gridSeriesData.max[POINT_EVENT_MS_IDX]);

        gridSeriesData.values.forEach(processSeriesData);
    };

    this.setData = function(context, settings, data) {

        if (data && data !== null) {
            // If the context already has a colour set then use it, otherwise set it to use this one.
            if (context) {
                if (context.color) {
                    colour = context.color;
                } else {
                    //context.color = colour;
                }
            }

            //#########################################################
            //Perform any visualisation specific data manipulation here
            //#########################################################

            //find all the unique nesting keys so we can synch series if needs be
            //commonFunctions.computeUniqueKeys(data);

            data.types[EVENT_COUNT_IDX] = "NUMBER";
            data.types[DATE_STR_IDX] = "TEXT";
            data.types[START_OF_DAY_MS_IDX] = "NUMBER";
            data.types[SESSION_START_MS_SINCE_START_OF_DAY_IDX] = "NUMBER";

            if (settings && settings.thresholdMs && typeof(parseInt(settings.thresholdMs, 10) == "number")){
                sessionThresholdMillis = parseInt(settings.thresholdMs);
            } else {
                sessionThresholdMillis = defaultSessionThresholdMillis;
            }

            data.values.forEach(processGridSeriesData);

            if (settings) {
                //Inspect settings to determine which axes to synch, if any.
                //Change the settings property(s) used according to the vis
                var synchedFields = [];
                //both x and y axes are defined by the data in fields 0 and 1
                if (commonFunctions.isTrue(settings.synchXAxis) || commonFunctions.isTrue(settings.synchYAxis)){
                    synchedFields.push(0);
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

                //Get grid to construct the grid cells and for each one call back into a 
                //new instance of this to build the visualisation in the cell
                //The last array arg allows you to synchronise the scales of fields
            }
            grid.buildGrid(context, settings, data, this, commonConstants.transitionDuration, synchedFields);
        }
    }


    var sessioniseData = function(seriesData, sessionThresholdMillis) {
        var arrLen = seriesData.values.length;
        var sessionId = 0;
        var lastEventTime = null;
        var sessionisedData = [];

        //loop through all the values for the series
        for (var i = 0; i < arrLen; i++){

            var eventTime = seriesData.values[i][POINT_EVENT_MS_IDX];

            if (lastEventTime == null){
                //first event of the series so create the first session, starting and ending on the same time
                sessionisedData[sessionId] = [];
                sessionisedData[sessionId][SESSION_START_MS_IDX] = eventTime;
                sessionisedData[sessionId][SESSION_END_MS_IDX] = eventTime;
                sessionisedData[sessionId][EVENT_COUNT_IDX] = 1;
            } else {
                if ((eventTime - lastEventTime) < sessionThresholdMillis) {
                    //within threshold so extend the current session
                    if (commonFunctions.truncateToStartOfDay(eventTime) != commonFunctions.truncateToStartOfDay(lastEventTime)) {
                        //this event is in a different day to the last one in the session so end the current session on midnight - 1milli
                        //and start a new on midnight
                        var lastEventDayMs = commonFunctions.truncateToStartOfDay(eventTime ) - 1;
                        sessionisedData[sessionId][SESSION_END_MS_IDX] = lastEventDayMs;
                        sessionisedData[sessionId][EVENT_COUNT_IDX]++;

                        var eventDayMs = commonFunctions.truncateToStartOfDay(eventTime);
                        sessionId++;
                        sessionisedData[sessionId] = [];
                        sessionisedData[sessionId][SESSION_START_MS_IDX] = eventDayMs;
                        sessionisedData[sessionId][SESSION_END_MS_IDX] = eventTime;
                        sessionisedData[sessionId][EVENT_COUNT_IDX] = 1;
                    } else {
                        sessionisedData[sessionId][SESSION_END_MS_IDX] = eventTime;
                        sessionisedData[sessionId][EVENT_COUNT_IDX]++;
                    }
                } else {
                    //outside threshold so make a new session
                    sessionId++;
                    sessionisedData[sessionId] = [];
                    sessionisedData[sessionId][SESSION_START_MS_IDX] = eventTime;
                    sessionisedData[sessionId][SESSION_END_MS_IDX] = eventTime;
                    sessionisedData[sessionId][EVENT_COUNT_IDX] = 1;
                }
            }
            lastEventTime = eventTime;


            //console.log("session " + sessionId + " from: " + new Date(sessionisedData[sessionId][SESSION_START_MS_IDX]) + " to: " + new Date(sessionisedData[sessionId][SESSION_START_MS_IDX]));
        }
        return sessionisedData;
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
        }
        //console.log("period start: " + new Date(data.min[SESSION_START_MS_IDX]) + " end: " + new Date(data.max[SESSION_END_MS_IDX]));
        //console.log("Series count: " + seriesCount);

        //The data has already been sessionised in setData() but we now need to augment the sesssion data points with 
        //stuff like the date string, sessionIds, time since start of day, etc.
        data.visibleValues().forEach(function(singleSeriesData) {
            //console.log("########Series: " + data.values[j].key);

            //now loop through the sessions and augment the data
            var sessionId = 0;
            singleSeriesData.values.forEach(function(pointData) {

                var hourOfDay = commonFunctions.getTwoDigitHourOfDay(pointData[SESSION_START_MS_IDX]);
                var dayMs = commonFunctions.truncateToStartOfDay(pointData[SESSION_START_MS_IDX]);

                //console.log("time: " + eventDate + " val: " + cellValue + " hour: " + hourOfDay + " day: " + new Date(dayMs));

                pointData[DATE_STR_IDX] = new Date(dayMs).toDateString();
                pointData[START_OF_DAY_MS_IDX] = dayMs;
                pointData[SESSION_START_MS_SINCE_START_OF_DAY_IDX] = commonFunctions.millisSinceStartOfDay(pointData[SESSION_START_MS_IDX]);
                pointData[SESSION_END_MS_SINCE_START_OF_DAY_IDX] = commonFunctions.millisSinceStartOfDay(pointData[SESSION_END_MS_IDX]);
                pointData[SESSION_ID_IDX] = sessionId++; 

                //used to aid debugging only
                pointData[SESSION_START_DATE_STR] = new Date(pointData[SESSION_START_MS_IDX]);
                pointData[SESSION_END_DATE_STR] = new Date(pointData[SESSION_END_MS_IDX]);

                //console.log("Session: " + i + " start: " + new Date(data.values[j].values[i][SESSION_START_MS_IDX]) + " end: " + new Date(data.values[j].values[i][SESSION_END_MS_IDX]));
            });
        });
        
        //add the string forms of all truncated day values for our data into an array that will be bound to the y axis.
        yAxisData = [];
        var startDayMs = commonFunctions.truncateToStartOfDay(data.min[SESSION_START_MS_IDX]);
        var timeMs = startDayMs;
        //var endDayMs = commonFunctions.truncateToStartOfDay(data.max[SESSION_END_MS_IDX]);
 
        while (timeMs <= data.max[POINT_EVENT_MS_IDX]){
            var dateStr = new Date(timeMs).toDateString();
            yAxisData.push(dateStr);
            timeMs += commonConstants.millisInDay;
        }

        var mode = (settings.synchSeries && settings.synchSeries.toLowerCase() == "true") ? "SYNCHED_SERIES" : "SERIES";
        if (typeof(context) === "undefined" || typeof(context.color) === "undefined") {
            commonFunctions.setColourDomain(colour, data, 0, mode);
        }

        visData = data;		
        update(0);
    }

    function dumpPoint(d){
        console.log("sessionStart: " + new Date(d[SESSION_START_MS_IDX]) + 
            " sessionEnd: " + new Date(d[SESSION_END_MS_IDX]) + 
            " eventCount: " + new Date(d[EVENT_COUNT_IDX]) + 
            " sessionStartMsSinceStartOfDay: " + d[SESSION_START_MS_SINCE_START_OF_DAY_IDX]);
    }

    var update = function(duration) {
        if (visData && visData != null) {
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

            var daysOnChart = yAxisData.length;
            var seriesCount = visibleValues.length;

            //always 24 hours in a day
            gridSizeX = Math.floor(width / 24);
            //work out the number of days in the data and add one so we have a cell for the max entry
            //gridSizeY = Math.min(gridSizeX * 1.5, Math.floor(height / daysOnChart));
            gridSizeY = height / daysOnChart;

            //padding between the outer most point and a swimlane
            gridYPadding = Math.round(gridSizeY * 0.1);

            //the height of space we allow points to ocupy in the swimlane
            usableGridSizeY =  gridSizeY - (gridYPadding * 2);

            //the height of a notional grid square in the usable part of the swimlane 
            subGridSizeY = usableGridSizeY / seriesCount;

            var yAxisLength = Math.min(gridSizeY * daysOnChart, height);

            var seriesData = visibleValues.map(function (d) {
                return d.key;
            });

            xSettingsTime = commonFunctions.createAxis(visData.types[SESSION_START_MS_SINCE_START_OF_DAY_IDX], 0, width);
            xScaleTime = xSettingsTime.scale;
            xSettingsTime.setExplicitDomain([0,24]);
            commonFunctions.buildAxis(xAxisContainer, xSettingsTime, "bottom", null, null, visSettings.displayXAxis);

            ySettingsOrdinal = commonFunctions.createAxis(visData.types[DATE_STR_IDX], height - yAxisLength, height);
            yScaleOrdinal = ySettingsOrdinal.scale;
            ySettingsOrdinal.setExplicitDomain(yAxisData);
            commonFunctions.buildAxis(yAxisContainer, ySettingsOrdinal, "left", null, null, visSettings.displayYAxis);

            ySettingsSubScale = commonFunctions.createAxis("ORDINAL", 0, usableGridSizeY);
            ySeriesSubScale = ySettingsSubScale.scale;
            ySettingsSubScale.setExplicitDomain(seriesData);

            //console.log(seriesData);

            if (commonFunctions.resizeMargins(margins, xAxisContainer, yAxisContainer) == true) {
                //console.log("Resized " + margins);
                update();
            } else {
                seriesContainer.call(tip);

                var g = seriesContainer.selectAll("g")
                    .data(visibleValues,
                            function(d) {
                                return d.key;
                            });

                var series = g.enter()
                    .append("svg:g");

                g.exit().transition()
                    .duration(commonConstants.transitionDuration)
                    .attr("opacity", "0")
                    .remove();

                //call the y axis and make the weekday labels bold
                yAxisContainer.transition()
                    .duration(commonConstants.transitionDuration)
                    .selectAll("text")
                    .attr("class", function(d) {
                        if (d.indexOf("Sun") == 0 || d.indexOf("Sat") == 0){
                            return  "weekend";
                        } else {
                            return "";    
                        }
                    });

                g.each(function(seriesData, i) {
                    var e = d3.select(this);

                    //add delegated mouse events to the series g element so it picks up all the mouse events of its children, i.e. the circles. 
                    //commonFunctions.addDelegateEvent(e, "mouseover", "line", pointMouseOverHandler);
                    //commonFunctions.addDelegateEvent(e, "mouseout", "line", pointMouseOutHandler);

                    //use a key function that returns the series name concatenated to the session start time
                    var series = e.selectAll("line")
                        .data(seriesData.values, function(d) { 
                            return d.key + "~~~" + d[SESSION_START_MS_IDX]; 
                        });

                    var fillColour = fillFunc(seriesData);

                    var seriesYOffset = Math.round(gridYPadding + ySeriesSubScale(seriesData.key) + (subGridSizeY / 2));

                    //console.log("Series i: " + i); 
                    //console.log("Series: " + d.key + " gridSizeY: " + gridSizeY + " offset: " + seriesYOffset + " padding: " + gridYPadding + " usableGridSizeY: " + usableGridSizeY);

                    series.enter().append("svg:line")
                        .attr("class", "symbol")
                        .style("fill-opacity", 1e-6)
                        .attr("opacity", 1e-6);

                    series.exit().transition()
                        .duration(commonConstants.transitionDuration)
                        .attr("opacity", "0")
                        .remove();

                    series.each(function(dataPoint) {

                        var graphPoint = d3.select(this);

                        var x1 = xScaleTime(dataPoint[SESSION_START_MS_SINCE_START_OF_DAY_IDX] / commonConstants.millisInHour) - 1;
                        var x2 = xScaleTime(dataPoint[SESSION_END_MS_SINCE_START_OF_DAY_IDX] / commonConstants.millisInHour) + 1;

                        //use the string version of the day axis
                        var y = yScaleOrdinal(dataPoint[DATE_STR_IDX]) + seriesYOffset;

                        //Fault finding code to highlight problems with scaling data points
                        if (isNaN(x1) || isNaN(x2)) {
                            dumpPoint(dataPoint);
                            console.log("INVALID X DATA - x1: " + x1 + 
                                " x2: " + x2 + 
                                " xSettingsTime.domain(): [" + xSettingsTime.domain() + "]" +
                                " key: " + seriesData.key);
                        }
                        if (isNaN(y)) {
                            dumpPoint(dataPoint);
                            console.log("INVALID Y DATA - " + 
                                " y: " + y + 
                                " yScaleOrdinal.domain(): [" + yScaleOrdinal.domain() + "]" +
                                " key: " + seriesData.key);
                        }

                        graphPoint.transition()
                            .duration(commonConstants.transitionDuration)
                            .attr("class", commonFunctions.makeColouredElementClassStringFunc(function(d) { return seriesData.key; }, "vis-session-element"))
                            .attr("opacity", "1")
                            .attr("x1", x1)
                            .attr("x2", x2)
                            .attr("y1", y)
                            .attr("y2", y)
                            .style("stroke-opacity", 1)
                            .style("fill", fillColour)
                            .style("fill-opacity", 1)
                            .style("stroke", fillColour)
                            .style("stroke-width", circleRadiusFunc(gridSizeX) + "px");
                    });
                    commonFunctions.addDelegateEvent(
                            e, 
                            "mouseover", 
                            "line", 
                            inverseHighlight.makeInverseHighlightMouseOverHandler(seriesData.key, visData.types, seriesContainer, "line"));

                    commonFunctions.addDelegateEvent(
                            e, 
                            "mouseout", 
                            "line", 
                            inverseHighlight.makeInverseHighlightMouseOutHandler(seriesContainer, "line"));
                });

                //construct the swim lanes
                var swimlanes = yAxisSwimLanesContainer
                    .selectAll("line")
                    .data(yAxisData, function (d) {return d;} );

                swimlanes.enter()
                    .append("svg:line")
                    .attr("class", "separatorLine")
                    .style("stroke", "#aaaaaa");

                swimlanes.exit()
                    .transition()
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
        }
    }

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
        return null;
    };
}
