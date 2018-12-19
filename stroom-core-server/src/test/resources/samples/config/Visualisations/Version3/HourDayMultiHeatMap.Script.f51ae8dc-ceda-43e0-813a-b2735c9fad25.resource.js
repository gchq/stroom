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
 * Visulisation to display multiple day/hour heat maps with the following dimensions:
 * x -           the hour of the day
 * y -           the day
 * cell colour - the value for that hour
 * 
 * Data is expected in the form of two dimensions, a millisecond time value truncated to the last hour (e.g. 23:00:00.000) (0) and the value at that time (1).  It
 * does not support multiple entries with the same time value.  Data should be truncated and aggregated before being passed to this visualisation.  The data
 * can optionally by grouped by a series value to produce multiple heat maps, keyed on the series name
 *
 * REQUIRES:
 * 		d3.js
 * 		d3-grid.js
 * 		Common.js
 * 		chroma.js
 * 		font-awesome
 */


//TODO this needs to be brought in line with the look/feel of the other visualisations, e.g. get rid of the background behind the grid cell titles

//TODO Ideally it would be nice to take some of the features of this vis, like the common axes that sit outside the grid cells and build that into GenericGrid.js so that other visualisations can use them.  Also this vis was the precursor to GenericGrid so it needs to be refactored to work with GenericGrid rather than doing its own thing.

//TODO The heat map needs to be made more generic so that the x and y axes can support non time series data, e.g. ordinal values.  The best way to do this would probably be to changed the input data to have two values, one for x and one for y.  The settings json could supply a time format string for datetime data (i.e. for rendering the ms since epoch as a day of the week or an hour of the day.  If you watend the standard day/hour heatmap then you would just supply the event time floored to the nearest hour for the x, and the event time floored to the nearest day for the y.  This assumes that Stroom will do all the aggregation and only one value array will be supplied for each heat map cell.  Doing all this would mean we can get rid of all the various non-gridded  heat map varients.
if (!visualisations) {
    var visualisations = {};
}
visualisations.HourDayMultiHeatMap = function() {

    var element = window.document.createElement("div");
    this.element = element;

    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;

    var m = [5, 5, 5, 5];
    var gridLayoutPadding = [0.07, 0.07];
    var chartHeaderTextHeight = 18;
    var greyColour = "#777";
    var heatMapColours = chroma.brewer.OrRd;

    var d3 = window.d3;
    //
    //inverse highlight mouse over support
    var inverseHighlight = commonFunctions.inverseHighlight();
    var tip = inverseHighlight.tip();

    //use a custom html function for d3-tip
    tip.html(function(tipData) { 

        var highlightedPointDayMs;
        var highlightedPointHour;
        var highlightedPointValue;
        var highlightedPointSeriesName;

        if (tipData.highlightedObj.classList.contains("vis-heatMapMinMaxCell")){
            //dealing with a highlighted cell
            highlightedPointDayMs = tipData.values.values[1];
            highlightedPointDay = tipData.values.values[3];
            highlightedPointHour = tipData.values.values[0];
            highlightedPointValue = tipData.values.values[2];
        } else {
            highlightedPointDayMs = tipData.values[1];
            highlightedPointDay = tipData.values[3];
            highlightedPointHour = tipData.values[0];
            highlightedPointValue = tipData.values[2];
        }

        //var dateValTxt = new Date(highlightedPointDayMs);
        var dateValTxt = highlightedPointDay;
        var timeValTxt = commonFunctions.pad(highlightedPointHour,2) + ":00";

        var html= inverseHighlight.htmlBuilder()
            .addTipEntry("Day",dateValTxt)
            .addTipEntry("Hour",timeValTxt)
            .addTipEntry("Value",commonFunctions.autoFormat(highlightedPointValue))
            .build();

        return html
    });

    var yAxisWidth = 120;
    var xAxisHeight = 20;
    var legendWidth = 30;

    var paddedCanvasWidthFunc = function() {
        return element.clientWidth - m[1] - m[3];
    }

    var paddedCanvasHeightFunc = function() {
        return element.clientHeight - m[0] - m[2];
    }

    var chartsAreaWidthFunc = function() {
        return paddedCanvasWidthFunc() - yAxisWidth - legendSpaceWidthFunc();
    }

    var chartsAreaHeightFunc = function() {
        return paddedCanvasHeightFunc() - xAxisHeight;
    }

    var legendSpaceWidthFunc = function() {
        return paddedCanvasWidthFunc() * 0.06;
    }

    var legendXPosFunc = function() {
        return 0 + (legendSpaceWidthFunc() * 0.1);
    }

    var highlightedStrokeWidthFunc = function(gridSizeX) {
        return Math.max(1, gridSizeX * 0.06) + "px";
    }

    var setCanvasDimensions = function (canvasNode) {
        canvasNode
            .attr("x",   "0px")
            .attr("y",    "0px")
            .attr("width", element.clientWidth  + "px")
            .attr("height", element.clientHeight + "px");
    };

    var setPaddedCanvasDimensions = function (paddedCanvasNode) {
        paddedCanvasNode
            .attr("x",   m[1] + "px")
            .attr("y",    m[0] + "px")
            .attr("width",  paddedCanvasWidthFunc() + "px")
            .attr("height", paddedCanvasHeightFunc() + "px");
    };

    var setXAxisCanvasDimensions = function (xAxisCanvasNode) {
        xAxisCanvasNode
            .attr("x",yAxisWidth + "px")
            .attr("y",paddedCanvasHeightFunc() - xAxisHeight + "px")
            .attr("width",  paddedCanvasWidthFunc() + "px")
            .attr("height", xAxisHeight + "px");
    };

    var setYAxisCanvasDimensions = function (yAxisCanvasNode) {
        yAxisCanvasNode
            .attr("x",   "0px")
            .attr("y", "0px")
            .attr("width",  yAxisWidth + "px")
            .attr("height", paddedCanvasHeightFunc() + "px");
    };

    var setLegendCanvasDimensions = function (legendCanvasNode) {
        legendCanvasNode
            .attr("x",   (paddedCanvasWidthFunc() - legendSpaceWidthFunc()) + "px")
            .attr("y", "0px")
            .attr("width",  legendSpaceWidthFunc() + "px")
            .attr("height", paddedCanvasHeightFunc() + "px");
    };

    var setChartsAreaCanvasDimensions = function (chartsAreaCanvasNode) {
        chartsAreaCanvasNode
            .attr("x",  yAxisWidth + "px")
            .attr("y", "0px")
            .attr("width", chartsAreaWidthFunc() + "px")
            .attr("height", chartsAreaHeightFunc() + "px");
    };

    var width = chartsAreaWidthFunc();
    var height = chartsAreaHeightFunc();

    var canvas = d3
        .select(element)
        .append("svg:svg")
        .attr("class", "vis-canvas");

    var paddedCanvas = canvas
        .append("svg:svg")
        .attr("class", "vis-paddedCanvas");

    var xAxisCanvas = paddedCanvas
        .append("svg:svg")
        .attr("class", "vis-xAxisCanvas");

    var yAxisCanvas = paddedCanvas
        .append("svg:svg")
        .attr("class", "vis-yAxisCanvas");

    var legendCanvas = paddedCanvas
        .append("svg:svg")
        .attr("class", "vis-legendCanvas");

    var chartsAreaCanvas = paddedCanvas
        .append("svg:svg")
        .attr("class", "vis-chartsAreaCanvas");

    //Add the series data.
    var seriesContainer = chartsAreaCanvas
        .append("svg:g")
        .attr("class", "vis-series");

    var dataPointKeyFunc = function(d) {
        //create a key for the dataItem such that it returns the mills of the hour since epoch
        return d[0] + (d[1] * commonConstants.millisInHour);
    }  	

    var legendKeyFunc = function(d) {
        //return the array position in the colors array
        return d.bucket;
    }

    var legend = legendCanvas.append("div")
        .attr("opacity","0")
        .attr("text-anchor", "middle")
        .attr("dy", ".3em")
        .style("color","red")
        .style("background","black")
        .style("font-size", "20px")
        .style("text-rendering", "geometricPrecision");

    var selectedSeries;

    var heatMapsGridLayout = d3.layout.grid()
        .bands()
        .size([width,height])
        .padding(gridLayoutPadding);

    var yAxisGridLayout = d3.layout.grid()
        .bands()
        .size([yAxisWidth,height])
        .cols(1)
        .padding(gridLayoutPadding);

    var xAxisGridLayout = d3.layout.grid()
        .bands()
        .size([width,xAxisHeight])
        .rows(1)
        .padding(gridLayoutPadding);

    var getHeatMapWidth = function(){
        return heatMapsGridLayout.nodeSize()[0];
    }

    var getHeatMapHeight = function(){
        return heatMapsGridLayout.nodeSize()[1];
    }
    var getUsableHeatMapHeight = function(){
        return getHeatMapHeight() - chartHeaderTextHeight;
    }

    var getHeatMapRows = function(){
        if (heatMapsGridLayout.size()[1] == 0){
            return 0;
        } else {
            return Math.round(heatMapsGridLayout.size()[1] / heatMapsGridLayout.nodeSize()[1]);
        }
    }

    var getHeatMapCols = function(){
        if (heatMapsGridLayout.size()[0] == 0){
            return 0;
        } else {
            return Math.round(heatMapsGridLayout.size()[0] / heatMapsGridLayout.nodeSize()[0]);
        }
    }

    var color = d3.scale.category20();
    var transitionDuration = 750;
    var buckets = 9; 
    var isZoomedIn = false;
    var bezierColourInterpolator = chroma.bezier([
            heatMapColours[0],
            heatMapColours[2],
            heatMapColours[4],
            heatMapColours[6],
            heatMapColours[8]]);

    var bucketValues = [];
    var legendData = [];
    var yAxisData = [];
    var minMaxData = [];
    var xScale;
    var yScale;
    var daysOnChart; 
    var backgroundColour = "#ffffff";
    var treeMapData;
    var allData;
    var visibleData;
    var visContext;
    var visSettings;
    var area;
    var min;
    var max;

    var setRectDimensions = function () {
        this.attr("x",   function(d) {return d.x +"px";}) 
            .attr("y",    function(d) {return d.y +"px";})
            .attr("width",  getHeatMapWidth() + "px")
            .attr("height", getHeatMapHeight() + "px");
    };

    function dumpPoint(d){
        console.log("hourOfDay: " + d[0] + " dayMs: " + d[1] + " day: " + new Date(d[1]) + " val: " + d[2]);
    }

    var pointMouseOverHandler = function (d) {
        var rootObj = this;
        d3.select(rootObj)
            .style("opacity", 0.5);
    };	



    var seriesZoomMouseHandler = function (d) {
        if (isZoomedIn) {
            //Zooming OUT so set visibleData back to allData
            //console.log("zooming out");
            visibleData = allData;
            update(transitionDuration);
            isZoomedIn = false;
        } else {
            //Zooming IN so truncate allData down to one series
            //console.log("zooming in");

            //Build a new object to hold just the series we want to see
            //but still use the min/max/sum/type from the full set so the legend doesn't change
            visibleData = {
                max: allData.max,
                min: allData.min,
                sum: allData.sum,
                types: allData.types,
                values: [d],
                seriesCount: 1
            };
            update(transitionDuration);

            isZoomedIn = true;
        }
    };

    var formatGridKey = function(key) {
        if (visSettings.gridSeriesDateFormat) {
            return commonFunctions.dateToStr(key, visSettings.gridSeriesDateFormat);
        } else {
            return key;
        }
    };

    this.setData = function(context, settings, d) {

        visSettings = settings;
        visContext = context;

        var seriesCount = d.values.length;

        //now build the legendData array based on the static colours array and newData
        bucketValues = commonFunctions.getBucketValues(d.min[1], d.max[1], buckets);
        min = d.min;
        max = d.max;

        legendData = commonFunctions.buildHeatMapLegendData(bucketValues, heatMapColours);

        //clear the data first
        yAxisData = [];
        //build a contiguous ordered array of days for our y axis
        yAxisData = commonFunctions.generateContiguousTimeArray(
                d.min[0],
                d.max[0],
                commonConstants.millisInDay,
                function(d) {return new Date(d).toDateString();  }
                );

        //console.log("min date: " + new Date(d.min[0]).toString() + " max date: " + new Date(d.max[0]).toString());
        //console.log("yAxisData: " + yAxisData);

        daysOnChart = commonFunctions.getDaysBetweenInclusive(d.min[0], d.max[0]);
        //console.log("daysOnChart: " + daysOnChart);

        //now munge the data for each series
        d.values.forEach(
                function(seriesData) {
                    seriesData = mungeSeriesData(seriesData, bucketValues);
                });

        //Put all the data in a variable and also set the current visible data to be all the data
        allData = d;
        visibleData = d;

        update(500);
    };

    var update = function(duration) {
        if (visibleData) {

            width = chartsAreaWidthFunc();
            height = chartsAreaHeightFunc();

            //update all the canvas dimensions based on the current window area
            setCanvasDimensions(canvas);
            setPaddedCanvasDimensions(paddedCanvas);
            setYAxisCanvasDimensions(yAxisCanvas);
            setXAxisCanvasDimensions(xAxisCanvas);
            setLegendCanvasDimensions(legendCanvas);
            setChartsAreaCanvasDimensions(chartsAreaCanvas);

            //Construct the grid layout for the heat maps
            heatMapsGridLayout = d3.layout.grid()
                .bands()
                .size([width,height])
                .padding(gridLayoutPadding);

            //console.log("Grid props - width:" + width + " height:" + height + " cols:" + heatMapsGridLayout.cols() + " rows:" + heatMapsGridLayout.rows());

            var heatMapRects = seriesContainer.selectAll(".vis-heatMap")
                .data(heatMapsGridLayout(visibleData.values), function(d) { 
                    return d.key; 
                });

            heatMapRects.enter()
                .append("svg:svg")
                .attr("class","vis-heatMap")
                .call(setRectDimensions)
                .call(function(parentNode) {
                    parentNode.append("svg:g")
                        .attr("class","vis-heatMapCells");
                    parentNode.append("svg:g")
                        .attr("class","vis-heatMapMinMaxCells");
                    //parentNode.append("svg:rect")
                    //.attr("x", 0)
                    //.attr("y", 0)
                    //.attr("width", "100%")
                    //.attr("height", "100%")
                    //.attr("class","vis-heatMap-border")
                    //.style("fill", "none")
                    //.style("stroke", "black")
                    //.style("stroke-width",1);
                    parentNode.append("svg:rect")
                        .attr("x", 0)
                        .attr("y", 0)
                        .attr("rx", 5)
                        .attr("ry", 5)
                        .attr("class","vis-cellVisualisation-textBackground")
                        .style("fill","rgba(30, 30, 30, 0.8)")
                        .style("stroke", "black")
                        .style("stroke-width",0);
                    parentNode.append("svg:text")
                        .attr("class","vis-heatMapSeriesName")
                        .attr("x", 5)
                        .style("cursor", "pointer")
                        .style("fill", "#eee")
                        .style("font-weight", "bold")
                        .on("mousedown",seriesZoomMouseHandler);
                    parentNode.append("svg:text")
                        .attr("class","vis-cellVisualisation-zoomIcon")
                        .style("font-family", "FontAwesome")
                        .style("cursor", "pointer")
                        .style("fill", "#eee")
                        .text(commonFunctions.zoomIconTextFunc(isZoomedIn))
                        .on("mousedown",seriesZoomMouseHandler);
                });

            heatMapRects.exit()
                .transition()
                .duration(duration)
                .style("opacity",0)
                .remove();

            heatMapRects.transition()
                .duration(duration)
                .style("opacity",1)
                .call(setRectDimensions)
                .call(function (parentNode) {
                    d3.selectAll(".vis-cellVisualisation-textBackground")
                        .attr("width", "100%")
                        .attr("height",chartHeaderTextHeight);
                    d3.selectAll(".vis-heatMapSeriesName")
                        .text(function (d) {
                            return formatGridKey(d.key);
                        })
                        .attr("y", chartHeaderTextHeight - 5);
                    d3.selectAll(".vis-cellVisualisation-zoomIcon")
                        .attr("x", getHeatMapWidth() - 15)
                        .attr("y", chartHeaderTextHeight - 4)
                        .text(commonFunctions.zoomIconTextFunc(isZoomedIn));
                    d3.selectAll(".vis-heatMapNameUnderline")
                        .attr("x2",getHeatMapWidth());
                });

            //console.log("Grid props - " 
            //+ "grid width:" + heatMapsGridLayout.size()[0] 
            //+ "grid height:" + heatMapsGridLayout.size()[1] 
            //+ "heatmap width:" + heatMapsGridLayout.nodeSize()[0] 
            //+ "heatmap height:" + heatMapsGridLayout.nodeSize()[1] 
            //+ " rows:" + getHeatMapRows() 
            //+ " cols:" + getHeatMapCols());

            //always 24 hours in a day
            gridSizeX = getHeatMapWidth() / 24;
            gridSizeY = Math.min((gridSizeX * 2), (getUsableHeatMapHeight() / daysOnChart));
            //console.log("gridsize x: " + gridSizeX + " y: " + gridSizeY);

            var xSettings;
            var zSettings;
            var xAxis;
            var yAxis;
            var yAxisBox;
            var xAxisLength = gridSizeX * 24;
            var yAxisLength = gridSizeY * daysOnChart;

            //xSettings = commonFunctions.createAxis("NUMBER", 0, getHeatMapWidth());
            //ySettings = commonFunctions.createAxis("ORDINAL", getHeatMapHeight() - yAxisLength, getHeatMapHeight());

            //xScale = xSettings.scale;
            //yScale = ySettings.scale;

            //Alter the number of xAxis ticks based on the axis length so they are not too squashed
            var xTicks = commonFunctions.getHourAxisTickMarkCount(xAxisLength);


            //xSettings.setDomain([0,24]);
            //ySettings.setDomain(yAxisData);
            //yAxis = ySettings.axis.tickValues(xSettings.axis.domain().filter(function(d,i) { return !(i % 2); }));
            
            xSettings = commonFunctions.createAxis("NUMBER", 0, getHeatMapWidth());
            xScale = xSettings.scale;
            xSettings.setExplicitDomain([0,24]);
            //commonFunctions.buildAxis(xAxisContainer, xSettings, "bottom", null, null, visSettings.displayXAxis);

            ySettings = commonFunctions.createAxis("ORDINAL", getHeatMapHeight() - yAxisLength, getHeatMapHeight());
            yScale = ySettings.scale;
            ySettings.setExplicitDomain(yAxisData);
            //commonFunctions.buildAxis(yAxisContainer, ySettings, "left", null, null, visSettings.displayYAxis);

            xAxis = xSettings.axis.orient("bottom").ticks(xTicks);
            yAxis = ySettings.axis.orient("left");

            buildXAxes(xAxis);
            buildYAxes(yAxis);
            

            var legendElementWidth = legendSpaceWidthFunc() * 0.1;
            var legendElementHeight = height / buckets;

            buildLegend(legendElementWidth, legendElementHeight);

            //for each rect in the grid, build a heat map
            heatMapRects.each(buildHeatMap);
        }
    };


    function buildXAxes(xAxis){

        //construct the grid for the y axes
        xAxisGridLayout = d3.layout.grid()
            .bands()
            .size([width,xAxisHeight])
            .rows(1)
            .padding(gridLayoutPadding);

        //only want one yaxis per row so that the first n sets of data
        var xAxisData = visibleData.values.slice(0, getHeatMapCols());	

        var xAxes = d3.select(".vis-xAxisCanvas")
            .selectAll(".xAxis")
            .data(xAxisGridLayout(xAxisData));

        xAxes.enter()
            .append("svg:g")
            .attr("class", "vis-axis" + " xAxis");

        //call the x axis and make the weekday labels bold
        xAxes.transition()
            .duration(transitionDuration)
            .call(xAxis.orient("bottom"))
            .attr("transform", function(d) {
                //return "translate(" + d.x + "," + (-1 * xAxisHeight) + ")";
                return "translate(" + d.x + ",1)";
            });

        xAxes.exit()
            .remove();
    }

    function buildYAxes(yAxis){

        //construct the grid for the y axes
        yAxisGridLayout = d3.layout.grid()
            .bands()
            .size([yAxisWidth,height])
            .cols(1)
            .padding(gridLayoutPadding);

        //only want one yaxis per row so that the first n sets of data
        var yAxisData = visibleData.values.slice(0, getHeatMapRows());	

        var yAxes = d3.select(".vis-yAxisCanvas")
            .selectAll(".yAxis")
            .data(yAxisGridLayout(yAxisData));

        var translateFunc = function(d) {
            return "translate(" + (yAxisWidth - 3) + "," + d.y + ")";
        };

        yAxes.enter()
            .append("svg:g")
            //.attr("class", staticClasses)
            .attr("class",  "vis-axis yAxis")
            .attr("transform", translateFunc);

        //call the y axis and make the weekend labels grey
        yAxes.transition()
            .duration(transitionDuration)
            .call(yAxis.orient("left"))
            .attr("transform", translateFunc)
            .selectAll("text")
            .attr("class", function(d) {
                if (d.indexOf("Sun") == 0 || d.indexOf("Sat") == 0){
                    return  "weekend";
                } else {
                    return "";	
                }
            });

        yAxes.exit()
            .remove();
    }

    var mungeSeriesData = function(d, buckets) {

        //work out the new min and max values
        var minX = 0;
        var minY = commonFunctions.truncateToStartOfDay(d.min[0]);
        var minZ = d.min[1];

        var maxX = 23;
        var maxY = commonFunctions.truncateToStartOfDay(d.max[0]);
        var maxZ = d.max[1];

        //clear the array
        minMaxData = [];

        var arrLen = d.values.length;

        for (var i = 0; i < arrLen; i++){
            var cellValue = d.values[i][1];
            var eventDate = new Date(d.values[i][0]);
            //var hourOfDay = commonFunctions.pad(eventDate.getHours(),2);
            var hourOfDay = eventDate.getHours();
            var dayMs = commonFunctions.truncateToStartOfDay(eventDate.getTime());
            var dayStr = new Date(dayMs).toDateString();

            //console.log("time: " + eventDate + " val: " + cellValue + " hour: " + hourOfDay + " day: " + new Date(dayMs));

            //now re-arrange the data points so 0/x=hour, 1/y=day, 2/z=cellValue
            d.values[i][0] = hourOfDay; 
            d.values[i][1] = dayMs;
            d.values[i][2] = cellValue;
            d.values[i][3] = dayStr;
            //console.log("hourOfDay: " + hourOfDay + " day: " + new Date(dayMs) + " val: " + cellValue);

            //capture the min and max values in a separate array object
            if (cellValue == minZ){
                minMaxData.push({key: d.key, type: "MIN", values: d.values[i]});
            } else if (cellValue == maxZ){
                minMaxData.push({key: d.key, type: "MAX", values: d.values[i]});
            }
        }

        //re-arrange the types
        d.types = [];
        d.types[0] = "HOUR";
        d.types[1] = "DAY";
        d.types[2] = "NUMBER";

        d.min[0] = minX;
        d.min[1] = minY;
        d.min[2] = minZ;

        d.max[0] = maxX;
        d.max[1] = maxY;
        d.max[2] = maxZ;

        maxVal = maxZ;
        //console.log(d);

        d.minMaxData = minMaxData;

        //console.log("minMaxData.length: " + minMaxData.length);

        return d;		
    }


    function buildLegend(legendElementWidth, legendElementHeight) {

        //display the legend blocks
        var legend = legendCanvas.selectAll("rect").data(legendData,legendKeyFunc);

        var paddingBeforeLegendElements = legendSpaceWidthFunc() * 0.05;

        //console.log("legendElementWidth:" + legendElementWidth + " legendXPosFunc():" + legendXPosFunc() + " legendSpaceWidthFunc():" + legendSpaceWidthFunc());

        legend.enter()
            .append("rect")
            .attr("class", "legendRect")
            .style("stroke", "#444444")
            .style("stroke-width", "1px");

        legend.transition()
            .duration(transitionDuration)
            .attr("x",  (paddingBeforeLegendElements + "px") )
            .attr("y", function(d, i) { 
                return height + (legendElementHeight * (-i - 1)) - 1; 
            })
        .attr("width", legendElementWidth)
            .attr("height", legendElementHeight)
            .style("fill", function(d, i) { 
                return d.rgbValue;
            });

        //now the legend text
        var legendText = legendCanvas.selectAll("text").data(legendData,legendKeyFunc);

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
                result = "<= " + commonFunctions.toSiUnits(val,1);
            }

            return result;
        }

        legendText.transition()
            .duration(transitionDuration)
            .text(legendTextValueFunction)
            .attr("x", paddingBeforeLegendElements + (legendElementWidth * 1.1) + "px")
            .attr("y", function(d, i) { 
                return height + (legendElementHeight * -i) - Math.floor(legendElementHeight / 2) - 1; 
            });
    }


    var buildHeatMap = function(data) {

        if (data != null) {

            //console.log("series: " + data.key + " seriesObject.length: " + data.values.length);

            var heatMapContainer = d3.select(this);
            var heatMapGroup = d3.select(this).select(".vis-heatMapCells");
            var minMaxGroup = d3.select(this).select(".vis-heatMapMinMaxCells");
            var highlightedPath;
            var maxVal;


            heatMapGroup.call(tip);
            //console.log("min: " + data.min[2] + " max: " + data.max[2]);


            //Alternate cell fill function using chroma to give an interpolated colour value
            var fillFunc = function(d) { 
                if (d[2] == 0) {
                    return backgroundColour;
                } else {
                    var colourFraction = (d[2] - min[1]) / (max[1] - min[1]);
                    return bezierColourInterpolator(colourFraction).hex();
                }
            }

            //Alternate cell fill function using only the colours in the pre-defined colour array
            //var colorScale = d3.scale.threshold()
            //.domain(bucketValues)
            //.range(legendData);

            //var fillFunc = function(d) { 
            //if (d[2] == 0) {
            //return backgroundColour;
            //} else {
            ////console.log("d.minute_count: " + d.minute_count + " colorScale(d.minute_count): " + colorScale(d.minute_count));
            //var legendDataPoint = colorScale(d[2]);

            ////we get undefined back if the value is essentially equal to the top of the range, e.g. if
            ////we have values 50,100,150 then d3 treats 150 as an exclusive threshold, so we make it inclusive
            //if (typeof legendDataPoint == 'undefined'){
            //return heatMapColours[buckets - 1];
            //} else {
            ////console.log("d.minute_count: " + d.minute_count + 
            ////      " colorScale(d.minute_count): " + colorScale(d.minute_count) + 
            ////      " colorScale(d.minute_count).rgbValue: " + colorScale(d.minute_count).rgbValue);
            //return colorScale(d[2]).rgbValue; 
            //}
            //}
            //}			

            //use a key function that returns the millis since epoch of each cell so the transitions work correctly
            var dataPoints = heatMapGroup.selectAll(".vis-heatMap-cell")
                .data(
                        data.values, 
                        function(d) { 
                            //return the absolute millis of the hour of the cell
                            return d.key + "_" + d[1] + (d[0] * commonConstants.millisInHour); 
                        });		

            dataPoints.enter()
                .append("svg:rect")
                .attr("class", "vis-heatMap-cell vis-heatMap-all-cells")
                .style("fill-opacity", 1e-6)
                .attr("opacity", 1e-6);

            dataPoints.exit().transition()
                .duration(transitionDuration)
                .attr("opacity", "0")
                .remove();

            dataPoints.each(function(point) {

                var cell = d3.select(this);
                var x = xScale(point[0]);

                //use the string version of the day axis
                var y = yScale(point[3]);

                if (isNaN(x) || isNaN(y)) {
                    //dumpPoint(point);
                    console.log("INVALID DATA - point[0]: " + point[0] + " x: " + x + " point[3]: " + point[3] + " y: " + y);

                }

                cell.transition()
                    .duration(transitionDuration)
                    .attr("opacity", "1")
                    .attr("x", x)
                    .attr("y", y)
                    .attr("width", gridSizeX)
                    .attr("height", gridSizeY)
                    .style("stroke-opacity", 1)
                    .style("fill", fillFunc(point))
                    .style("fill-opacity", 1)
                    .style("stroke", "none")
                    .style("stroke-width", "0");
            });

            //handle the min and max cell highlighting
            //this needs to be done as a separate data binding with new svg elements as if we just added a different stroke style to the
            //cells above for min/max they will be obscured by the cells around them as we have no control of the z order

            var minMaxCells = minMaxGroup.selectAll(".vis-heatMapMinMaxCell").data(data.minMaxData,
                    function(d) {
                        //console.log("keyFuncVal:" + d.key + "_" + (d.values[0] + (d.values[1] * commonConstants.millisInHour)) + "_" + d.type);
                        return (d.key + "_" + (d.values[0] + (d.values[1] * commonConstants.millisInHour))) + "_" + d.type;
                    });

            minMaxCells.enter()
                .append("svg:rect")
                .attr("class", function(d) {
                    //return "vis-heatMapMinMaxCell vis-heatmap-all-cells " + d.type.toLowerCase() + " " + d.values[0] + " " + d.values[1] + " " + d.key; 
                    return "vis-heatMapMinMaxCell vis-heatMap-all-cells " + d.type.toLowerCase() ; 
                })
            .attr("x", function(d) {return xScale(d.values[0]); })
                .attr("y", function(d) {return yScale(d.values[3]); })
                .attr("width", gridSizeX)
                .attr("height", gridSizeY)
                .attr("stroke-dasharray", function() { return gridSizeX * 0.2; })
                .style("fill-opacity", 1e-6)
                .attr("opacity", 1e-6);

            minMaxCells
                .transition()
                .duration(transitionDuration )  
                .attr("opacity", "1")
                .attr("x", function(d) {return xScale(d.values[0]); })
                .attr("y", function(d) {return yScale(d.values[3]); })
                .attr("width", gridSizeX)
                .attr("height", gridSizeY)
                .attr("stroke-dasharray", function() { return gridSizeX * 0.2; })
                .style("fill", function(d) {
                    //min/max 'd' is different to normal cells so wrap it like this
                    return fillFunc(d.values);
                })
            .style("fill-opacity", 1)
                .style("stroke-opacity", 1)
                .style("stroke", function(d) {
                    return d.type == "MIN" ? "#00ff00" : "#ff0000"; 
                })
            .style("stroke-width", highlightedStrokeWidthFunc(gridSizeX) );

            minMaxCells.exit()
                .transition()
                .duration(0)
                .attr("opacity", 1e-6)
                .style("stroke-opacity", 1e-6)
                .remove();

            //add delegated mouse events to the whole heatmap so it can cope with both normal cells and min/max cells
            var cellCssSelector = ".vis-heatMap-all-cells";
            commonFunctions.addDelegateEvent(heatMapContainer, "mouseover",cellCssSelector, inverseHighlight.makeInverseHighlightMouseOverHandler(null, data.types, heatMapContainer,cellCssSelector));
            commonFunctions.addDelegateEvent(heatMapContainer, "mouseout",cellCssSelector, inverseHighlight.makeInverseHighlightMouseOutHandler(heatMapContainer,cellCssSelector));
        }
    }


    //var onMouseDown = function() {
    //var eventTarget = d3.event.target;
    //var mousePos = d3.mouse(eventTarget);

    //legend.attr("opacity","1")
    //.attr("transform", "translate("+mousePos[0]+","+mousePos[1]+")")
    //.text(eventTarget.id);

    //};

    //var onMouseUp = function() {

    //legend.attr("opacity","0")
    //.text("");

    //};

    //paddedCanvas.on("mousedown", onMouseDown);
    //paddedCanvas.on("mouseup", onMouseUp);

    function zoom(d) {
        treeMap.nodes(d);
    }

    this.resize = function() {
        var newWidth = element.clientWidth - m[1] - m[3];
        var newHeight = element.clientHeight - m[0] - m[2];
        if (newWidth != width || newHeight != height) {
            update(0);
        }
    };

}
