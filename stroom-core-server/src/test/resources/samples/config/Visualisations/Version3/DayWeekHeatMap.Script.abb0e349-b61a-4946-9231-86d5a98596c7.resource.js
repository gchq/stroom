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
 * Visulisation to display a day/week heat map with the following dimensions:
 * x -           the week
 * y -           the day of the week
 * cell colour - the value for that day
 * 
 * Data is expected in the form of two dimensions, a millisecond time value truncated to the last day (e.g. Mon 00:00:00.000) (0) and the value at that time (1).  It
 * does not support multiple entries with the same time value.  Data should be truncated and aggregated before being passed to this visualisation.
 * 
 */



if (!visualisations) {
    var visualisations = {};
}
visualisations.DayWeekHeatMap = function() {
    var element = window.document.createElement("div");
    this.element = element;

    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;

    var visSettings;
    var visContext;

    //ensure we have the correct instance of d3
    var d3 = window.d3;	

    var margins = commonConstants.margins();

    var transitionDuration = 500;

    var legendSpaceWidthFunc = function() {
        return Math.floor(element.clientWidth * 0.07);
    }

    var widthFunc = function() {
        return element.clientWidth - margins.right - margins.left - legendSpaceWidthFunc();
    }

    var heightFunc = function() {
        return element.clientHeight - margins.top - margins.bottom;
    }

    var legendXPosFunc = function() {
        return width + (legendSpaceWidthFunc() * 0.1);
    }

    var cellRadiusFunc = function(gridSizeX) {
        return Math.round(gridSizeX * 0.2);
    }

    var highlightedStrokeWidthFunc = function(gridSizeX) {
        return Math.max(1, Math.round(gridSizeX * 0.06)) + "px";
    }



    var width = widthFunc();
    var height = heightFunc();


    var buckets = 9;



    var backgroundColour = "#ffffff";
    var cellStrokeColour = "#ffffff";
    var cellStrokeColourHighlighted = "#000000";
    var cellStrokeWidth = "1px";

    //max width or height of a grid cell
    var maxGridSize = 80;


    var xSettings;
    var zSettings;
    var xScale;
    var yScale;
    var xAxis;
    var yAxis;
    var highlightedPath;

    var canvas = d3.select(element).append("svg:svg");

    var svg = canvas.append("svg:g");

    // Create a colour set.
    var colour = d3.scale.category20();

    // Add the series data.
    var seriesContainer = svg.append("svg:g").attr("class", "vis-series");

    // Add the x-axis.
    var xAxisContainer = svg.append("svg:g").attr("class",
            "vis-axis" + " xAxis");

    // Add the y-axis.
    var yAxisContainer = svg.append("svg:g").attr("class",
            "vis-axis" + " yAxis");

    // Add the y-axis.
    var xLegendContainer = svg.append("svg:g").attr("class",
            "vis-legend" + " xLegend");

    var minMaxContainer = svg.append("svg:g").attr("class",
            "vis-min-max");

    var getScale = function(type, min, max) {
        if (type == "DATE_TIME") {
            return d3.time.scale.utc().range([ min, max ]);
        } else if (type == "NUMBER") {
            return d3.scale.linear().range([ min, max ]);
        } else {
            return d3.scale.ordinal().rangeRoundBands([ min, max ], 0);
        }
    }





    var dataPointKeyFunc = function(d) {
        //create a key for the dataItem such that it returns the mills of the hour since epoch
        return d[0] + (d[1] * commonConstants.millisInHour);
    }




    var pointMouseOverHandler = function (d) {

        var rootObj = this;

        d3.select(rootObj)
            .style("opacity", 0.5);
    };			


    var pointMouseMoveHandler = function (d) {

        var rootObj = this;

        var highlightedPoint = d3.select(rootObj);

        var highlightedPointDayMs;
        var highlightedPointValue;

        if (d.hasOwnProperty('type')){
            //dealing with a highlighted cell
            highlightedPointDayMs = d.values[0];
            highlightedPointValue = d.values[1];
        }else {
            highlightedPointDayMs = d[0];
            highlightedPointValue = d[1];
        }

        var xValTxt = new Date(highlightedPointDayMs);
        var yValTxt = Math.round((highlightedPointValue + 0.00001) * 1000) / 1000;

        commonFunctions.addDataPointLabel(element, margins, null, xValTxt, yValTxt);
    }

    var pointMouseOutHandler = function (d) {
        var rootObj = this; 
        d3.select(rootObj)
            .style("opacity", 1);

        d3.select(element).select("div.vis-label").remove();    	
    };    


    var data;
    var legendData = [];
    var maxVal;
    var minMaxData = [];
    var bucketValues = [];
    var xAxisData = [];

    this.setData = function(context, settings, d) {	
        /*
         * What we get:
         * d[0] - timeMs (truncated to last day)
         * d[1] - the value (i.e. the heat) at that time
         */

        /*
         * This is what we are trying to build
         * d[0] - timeMs (truncated to last day)
         * d[1] - the value (i.e. the heat) at that time
         * d[2] - Week commencing string - the time as a string, truncated to the last monday
         * d[3] - the day of week in string form. e.g. ["Mon","Tue","Wed","Thur","Fri","Sat","Sun"]
         * d[4] - year/month as y-m
         */

        visContext = context;
        visSettings = settings;

        //work out the new min and max values
        var minZ = d.min[1];
        var maxZ = d.max[1];

        //clear the array
        minMaxData = [];
        xAxisData = [];

        var arrLen = d.values.length;

        for (var i = 0; i < arrLen; i++){
            var cellValue = d.values[i][1];
            var eventDate = new Date(d.values[i][0]);

            //get the date of 00:00:00 on the last monday before the event time
            var dateFormat = d3.time.format("%d-%m-%y");			

            //work out day of the week 0-6, 0==Mon
            var dayOfWeekNo = eventDate.getDay() - 1 ;
            if (dayOfWeekNo == -1) {
                dayOfWeekNo = 6;
            }

            var weekComencingDate = commonFunctions.getWeekCommencingDate(eventDate);
            var weekComencingStr = dateFormat( weekComencingDate );
            //console.log(eventDate + " - " + weekComencingDate + " - " + weekComencingStr);

            //map the day no to one of our array values
            var dayOfWeekStr = commonConstants.days[dayOfWeekNo];

            var yearMonthStr = eventDate.getYear() + "-" + eventDate.getMonth();

            d.values[i][2] = weekComencingStr;
            d.values[i][3] = dayOfWeekStr;
            d.values[i][4] = yearMonthStr;

            //capture the min and max values in a separate array object
            if (cellValue == minZ){
                minMaxData.push({type: "MIN", values: d.values[i]});
            } else if (cellValue == maxZ){
                minMaxData.push({type: "MAX", values: d.values[i]});
            }

            //dumpPoint(d.values[0].values[i]);
            //console.log(d.values[i]);
        }


        //build a contiguous array of dates for our x axis
        xAxisData = commonFunctions.generateContiguousTimeArray(
                commonFunctions.getWeekCommencingDate(new Date(d.min[0])).getTime(),
                commonFunctions.getWeekCommencingDate(new Date(d.max[0])).getTime(),
                commonConstants.millisInWeek,
                function(d) {return dateFormat( new Date(d) ) }
                );

        //hard code the new types
        d.types[2] = "WEEK";
        d.types[3] = "DAY";
        d.types[4] = "MONTH";

        //now build the legendData array based on the static colours array and newData
        bucketValues = commonFunctions.getBucketValues(d.min[1], d.max[1], buckets);

        legendData = commonFunctions.buildHeatMapLegendData(bucketValues);

        //console.log(xAxisData);

        data = d;		
        update();
    }


    var legendKeyFunc = function(d) {
        //return the array position in the colours array
        return d.bucket;
    }


    function dumpPoint(d){
        console.log("timeMs: " + d[0] + " timeStr: " + new Date(d[0]) + " week: " + d[2] + " day: " + d[3] + " month: " + d[4] + " val: " + d[1] );
    }

    var update = function() {
        if (data != null) {

            //console.log("min: " + data.min[2] + " max: " + data.max[2]);

            width = widthFunc();
            height = heightFunc();

            //var weeksOnChart = (Math.floor(getWeekCommencingDate(data.max[0]).getTime() / millisInWeek) - Math.floor(getWeekCommencingDate(data.min[0]).getTime() / millisInWeek) ) + 1;

            var weeksOnChart = Math.ceil((commonFunctions.getWeekCommencingDate(new Date(data.max[0])).getTime() - commonFunctions.getWeekCommencingDate(new Date(data.min[0])).getTime()) / commonConstants.millisInWeek) + 1;

            //console.log("weeksOnChart: " + weeksOnChart);

            //scale according to number of weeks in data
            gridSizeX = Math.min(maxGridSize, Math.floor(width / weeksOnChart));
            //always 7 days in y axis
            gridSizeY = Math.min(maxGridSize, Math.floor(height / 7));

            //console.log("desired -  gridX: " + gridSizeX + " gridY: " + gridSizeY);

            var legendElementWidth = gridSizeX / 3;
            var legendElementHeight = Math.floor(height / buckets);

            canvas
                .attr("width", element.clientWidth)
                .attr("height",element.clientHeight);

            svg.attr("transform", "translate(" + margins.left + "," + margins.top + ")");

            xAxisContainer.attr("transform", "translate(0," + height + ")");

            //xLegendContainer.attr("transform", "translate(0," + (height - legendElementHeight) + ")");

            var colourScale = d3.scale.threshold()
                .domain(bucketValues)
                .range(legendData);

            var fillFunc = function(d) { 
                if (d[1] == 0) {
                    return backgroundColour;
                } else {
                    var legendDataPoint = colourScale(d[1]);

                    //we get undefined back if the value is essentially equal to the top of the range, e.g. if
                    //we have values 50,100,150 then d3 treats 150 as an exclusive threshold, so we make it inclusive
                    if (typeof legendDataPoint == 'undefined'){
                        return commonConstants.heatMapColours[buckets - 1];
                    } else {
                        return colourScale(d[1]).rgbValue; 
                    }
                }
            }			

            var xAxisLength = gridSizeX * weeksOnChart;
            var yAxisLength = gridSizeY * 7;	

            //xSettings = commonFunctions.createAxis(data.types[2], 0, xAxisLength);
            //ySettings = commonFunctions.createAxis(data.types[3], height - yAxisLength, height);
            //xScale = xSettings.scale;
            //yScale = ySettings.scale;
            //xAxis = xSettings.axis.orient("bottom");
            //yAxis = ySettings.axis.orient("left");

            //xSettings.setDomain(xAxisData);
            //ySettings.setDomain(commonConstants.days);

            xSettings = commonFunctions.createAxis(data.types[2], 0, xAxisLength);
            xScale = xSettings.scale;
            xSettings.setExplicitDomain(xAxisData);
            commonFunctions.buildAxis(xAxisContainer, xSettings, "bottom", null, null, visSettings.displayXAxis);

            ySettings = commonFunctions.createAxis(data.types[3], height - yAxisLength, height);
            yScale = ySettings.scale;
            ySettings.setExplicitDomain(commonConstants.days);
            commonFunctions.buildAxis(yAxisContainer, ySettings, "left", null, null, visSettings.displayYAxis);

            if (commonFunctions.resizeMargins(margins, xAxisContainer, yAxisContainer) == true) {
                update();
            } else {

            var g = seriesContainer.selectAll("g").data(data.values,
                    function(d) {
                        return d.key;
                    });

            var series = g.enter().append("svg:g");

            g.exit().transition()
                .duration(transitionDuration)
                .attr("opacity", "0")
                .remove();


            //xAxisContainer.transition()
            //.duration(transitionDuration)
            //.call(xAxis.orient("bottom"));

            //call the y axis and make the weekday labels bold
            yAxisContainer.transition()
                .duration(transitionDuration)
                //.call(yAxis.orient("left"))
                .selectAll("text")
                .style("font-weight", function(d) {
                    if (d.indexOf("Sun") == 0 || d.indexOf("Sat") == 0){
                        return "normal";
                    }else{this
                        return "bold";	
                    }
                });

            //use a key function that returns the millis since epoch of each cell so the transitions work correctly
            var dataPoints = seriesContainer.selectAll("rect")
                .data(data.values, function(d) { return d[0]; });

            dataPoints.enter().append("svg:rect").attr("class", "symbol")
                .style("fill-opacity", 1e-6)
                .attr("opacity", 1e-6);

            dataPoints.exit().transition()
                .duration(transitionDuration)
                .attr("opacity", "0")
                .remove();

            dataPoints.each(function(point) {

                var cell = d3.select(this);
                var x = xScale(point[2]);

                //use the string version of the day axis
                var y = yScale(point[3]);

                //					if (isNaN(x) || isNaN(y)) {
                //						dumpPoint(point);
                //					}

                cell.transition()
                    .duration(transitionDuration)
                    .attr("opacity", "1")
                    .attr("x", x)
                    .attr("y", y)
                    .attr("rx", cellRadiusFunc(gridSizeX))
                    .attr("ry", cellRadiusFunc(gridSizeX))
                    .attr("width", gridSizeX)
                    .attr("height", gridSizeY)
                    .style("stroke-opacity", 1)
                    .style("fill", fillFunc(point))
                    .style("fill-opacity", 1)
                    .style("stroke", cellStrokeColour)
                    .style("stroke-width", cellStrokeWidth);
            });


            //handle the min and max cell highlighting

            var minMaxCells = minMaxContainer.selectAll("rect").data(minMaxData,
                    function(d) {
                        //append the type (MIN|MAX) on the end
                        return d.values[0] + "_" + d.type;
                    });

            minMaxCells.enter()
                .append("svg:rect")
                .attr("class", function(d) {return d.type.toLowerCase(); })
                .style("fill-opacity", 1)
                .style("fill", function(d) {return fillFunc(d.values); })
                .attr("opacity", 1e-6);

            minMaxCells.exit().transition()
                .duration(0)
                .attr("opacity", "0")
                .style("stroke-opacity", 0)
                .remove();

            minMaxCells.transition()
                .duration(transitionDuration * 2)  
                .attr("opacity", "1")
                .attr("x", function(d) {return xScale(d.values[2]); })
                .attr("y", function(d) {return yScale(d.values[3]); })
                .attr("rx", cellRadiusFunc(gridSizeX))
                .attr("ry", cellRadiusFunc(gridSizeX))
                .attr("width", gridSizeX)
                .attr("height", gridSizeY)
                .style("stroke-opacity", 1)
                .style("stroke", function(d) {
                    return d.type == "MIN" ? "#00ff00" : "#ff0000"; 
                })
            .style("stroke-width", highlightedStrokeWidthFunc(gridSizeX) );

            //add delegated mouse events to the series g element so it picks up all the mouse events of its children, i.e. the circles. 
            commonFunctions.addDelegateEvent(seriesContainer, "mouseover", "rect", pointMouseOverHandler);
            commonFunctions.addDelegateEvent(seriesContainer, "mousemove", "rect", pointMouseMoveHandler);
            commonFunctions.addDelegateEvent(seriesContainer, "mouseout", "rect", pointMouseOutHandler);

            commonFunctions.addDelegateEvent(minMaxContainer, "mouseover", "rect", pointMouseOverHandler);
            commonFunctions.addDelegateEvent(minMaxContainer, "mousemove", "rect", pointMouseMoveHandler);
            commonFunctions.addDelegateEvent(minMaxContainer, "mouseout", "rect", pointMouseOutHandler);

            //display the legend blocks
            var legend = xLegendContainer.selectAll("rect").data(legendData,legendKeyFunc);

            legend.enter()
                .append("rect")
                .attr("class", "legendRect")
                .style("stroke", "#444444")
                .style("stroke-width", "1px");

            legend.transition()
                .duration(transitionDuration)
                .attr("x", width + (legendSpaceWidthFunc() * 0.05) )
                .attr("y", function(d, i) { 
                    return height + (legendElementHeight * (-i - 1)) - 1; 
                })
            .attr("width", legendElementWidth)
                .attr("height", legendElementHeight)
                .style("fill", function(d, i) { 
                    return commonConstants.heatMapColours[i]; 
                });

            //now the legend text
            var legendText = xLegendContainer.selectAll("text").data(legendData,legendKeyFunc);

            legendText.enter()
                .append("text")
                .attr("class", "legendText mono");

            var legendTextValueFunction = function(d) { 
                //console.log(d);
                //console.log("d.thresholdValue; " + d.thresholdValue);
                var val = d.thresholdValue;
                var result = "";

                if (val == 0) {
                    result = "> 0";
                } else {
                    result = "<= " + commonFunctions.toSiUnits(val,3);
                }

                return result;
            }

            legendText.transition()
                .duration(transitionDuration)
                .text(legendTextValueFunction)
                .attr("x", legendXPosFunc() + Math.floor(legendElementWidth * 1.1))
                .attr("y", function(d, i) { 
                    return height + (legendElementHeight * -i) - Math.floor(legendElementHeight / 2) - 1; 
                });
            }
        }
    }

    this.resize = function() {
        var newWidth = element.clientWidth - margins.right - margins.left;
        var newHeight = element.clientHeight - margins.top - margins.bottom;

        if (newWidth != width || newHeight != height) {
            update();
        }
    }
}
