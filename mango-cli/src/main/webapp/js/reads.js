//Configuration Variables
var readsHeight = 30; //Default variable: this will change based on number of reads
var yOffset = 175;

// Global Data
var sampleData = [];
// Svg Containers, and vertical guidance lines and animations set here for all divs

//Manages changes when clicking checkboxes
d3.selectAll("input").on("change", checkboxChange);

// Create the scale for the axis
var refAxisScale = d3.scale.linear()
    .domain([viewRegStart, viewRegEnd])
    .range([0, width]);

// Create the axis
var refAxis = d3.svg.axis()
   .scale(refAxisScale);

var readsSvgContainer = {};

for (var i = 0; i < samples.length; i++) {
  $("#readsArea").append("<div id=\"" + samples[i] + "\" class=\"samples resize-vertical\"></div>");
  $("#"+samples[i]).append("<div class=\"sampleLegend\"></div>");
  $("#"+samples[i]).append("<div class=\"sampleCoverage\"></div>");
  $("#"+samples[i]).append("<div class=\"mergedReads\"></div>");
  $("#"+samples[i]).append("<div class=\"alignmentData\"></div>");

  var width = $(".mergedReads").width();

  var selector = "#" + samples[i] + ">.mergedReads";
  readsSvgContainer[samples[i]] = d3.select(selector)
    .append("svg")
      .attr("height", (readsHeight))
      .attr("width", width);

  var readsVertLine = readsSvgContainer[samples[i]].append('line')
    .attr({
      'x1': 50,
      'y1': 0,
      'x2': 50,
      'y2': readsHeight
    })
    .attr("stroke", "#002900")
    .attr("class", "verticalLine");

  readsSvgContainer[samples[i]].on('mousemove', function () {
    var xPosition = d3.mouse(this)[0];
    d3.selectAll(".verticalLine")
      .attr({
        "x1" : xPosition,
        "x2" : xPosition
      })
  });

}

// print file name
function renderSamplename(i) {
    if ($("#" + samples[i] + ">.sampleLegend>.title").length < 1) {
        var selector = "#" + samples[i] + ">.sampleLegend";
        $(selector + ">.title").remove();
        $(selector).append("<div class='col-md-9 title'>" + rawSamples[i] + "</div>");
        $(selector).append("<div class='col-md-3'><input value='viewAlignments' name='viewAlignments'" +
                                "type='checkbox'onClick='toggleAlignments(\"" + samples[i] + "\")' id='viewAlignments'>" +
                                "<label for='viewAlignments'>Alignments</label></div>");
        $( "#" + samples[i] + ">.alignmentData")[0].hidden = true; // TODO: change to jQuery

    }
}

function toggleAlignments(selector) {
    var alignmentSelector =  $("#" + selector + ">.alignmentData");
    if (alignmentSelector[0].hidden) {
       alignmentSelector[0].hidden = false; // TODO: change to jQuery
       renderAlignments(viewRefName, viewRegStart, viewRegEnd, mapQuality, selector);
    }
    else {
       alignmentSelector[0].hidden = true; // TODO: change to jQuery
    }
}

function renderMergedReads(refName, start, end, quality) {
    renderReads(refName, start, end, quality, true)
}

function renderAlignments(refName, start, end, quality, selector) {
    renderReads(refName, start, end, quality, false, sampleMap[selector])
}

function renderReads(refName, start, end, quality, isCountData, samples) {
    quality = quality || 0
    var samples = typeof samples != "undefined" ? samples : sampleId;
    var jsonPage = "reads"
    if (isCountData)
         jsonPage = "mergedReads";
    else
        jsonPage = "reads";
    var readsJsonLocation = "/" + jsonPage + "/" + viewRefName + "?start=" + viewRegStart + "&end="
        + viewRegEnd + "&sample=" + samples + "&quality=" + quality;

    if (isCountData)
        renderJsonMergedReads(readsJsonLocation);
    else
        renderJsonReads(readsJsonLocation, Array(samples));
}

function renderJsonMergedReads(readsJsonLocation) {

  d3.json(readsJsonLocation,function(error, ret) {
    for (var i = 0; i < samples.length; i++) {
        var data = typeof ret[rawSamples[i]] != "undefined" ? ret[rawSamples[i]] : [];
        var selector = "#" + samples[i];
        sampleData[i] = [];
        sampleData[i].mismatches = typeof data['mismatches'] != "undefined" ? data['mismatches'] : [];
        sampleData[i].indels = typeof data['indels'] != "undefined" ? data['indels'] : [];

        renderSamplename(i);
        renderJsonCoverage(data['freq'], i);
        renderMismatchCounts(sampleData[i].mismatches, samples[i]);
        renderIndelCounts(sampleData[i].indels, samples[i]);
    }
  });
}

function renderJsonReads(readsJsonLocation, samples) {

  d3.json(readsJsonLocation,function(error, ret) {
  for (var i = 0; i < samples.length; i++) {
      var readsData = typeof ret[samples[i]] != "undefined" ? ret[samples[i]] : [];
      console.log(readsData);
     // render reads for low or high resolution depending on base range
     if (viewRegEnd - viewRegStart > 100) {
       renderReadsByResolution(false, readsData, samples[i]);
     } else {
       renderReadsByResolution(true, readsData, samples[i]);
     }
    }
  });

}

// Renders reads by resolution
function renderReadsByResolution(isHighRes, data, rawSample) {

        var readTrackHeight = getTrackHeight();
        var readDiv = [];
        var container = [];
        var svgClass = "alignmentSvg";

        var sample = filterName(rawSample);
        var selector = "#" + sample + ">.alignmentData";

        // check whether alignment container for this sample was already rendered
        if ($(selector + ">." + svgClass).length == 0) {
            container = d3.select(selector)
                .append("svg")
                .attr("class", svgClass)
                .attr("height", (readsHeight))
                .attr("width", width);
        } else {
            container = d3.select(selector).selectAll("svg");
        }

        var removed = container.selectAll("g").remove();

        // Making hover box
        readDiv[i] = d3.select(selector)
          .append("div")
          .attr("class", "tooltip")
          .style("opacity", 0);

        // Create the scale for the axis
        var readsAxisScale = d3.scale.linear()
            .domain([viewRegStart, viewRegEnd])
            .range([0, width]);

        // Create the axis
        var readsAxis = d3.svg.axis()
           .scale(readsAxisScale);

        // Remove current axis to update it
        container.select(".axis").remove();

        var selector = "#" + sample;
        sampleData[i] = [];
        sampleData[i].reads = typeof data['tracks'] != "undefined" ? data['tracks'] : [];
        sampleData[i].mismatches = typeof data['mismatches'] != "undefined" ? data['mismatches'] : [];
        sampleData[i].indels = typeof data['indels'] != "undefined" ? data['indels'] : [];
        sampleData[i].pairs = typeof data['matePairs'] != "undefined" ? data['matePairs'] : [];
        sampleData[i].filename = typeof data['filename'] != "undefined" ? data['filename'] : "";

        // Renders Reads Frequency
        renderJsonCoverage(data['freq'], i)

        renderSamplename(i);
        var numTracks = d3.max(data["tracks"], function(d) {return d.track});
        numTracks = typeof numTracks != "undefined" ? numTracks : [];

        readsHeight = (numTracks+1)*readTrackHeight;

        // Reset size of svg container
        container.attr("height", (readsHeight));

        // Add the axis to the container
        container.append("g")
          .attr("class", "axis")
          .attr("transform", "translate(0, " + readsHeight + ")")
          .call(readsAxis);

        // Update height of vertical guide line
        $(".verticalLine").attr("y2", readsHeight);

        //Add the rectangles
        var rects = container.selectAll(".readrect").data(data["tracks"]);
        var modify = rects.transition();

      modify
        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
        .attr("y", (function(d) { return readsHeight - readTrackHeight * (d.track+1); }))
        .attr("height", (readTrackHeight-1))
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

      var newData = rects.enter();
      newData
        .append("g")
        .append("rect")
        .attr("class", "readrect")
        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
        .attr("y", (function(d) { return readsHeight - readTrackHeight * (d.track+1); }))
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
        .attr("height", (readTrackHeight-1))
        .style("fill", "#B8B8B8")
          .on("click", function(d) {
            readDiv[i].transition()
              .duration(200)
              .style("opacity", .9);
            readDiv[i].html(
              "Read Name: " + d.readName + "<br>" +
              "Start: " + d.start + "<br>" +
              "End: " + d.end + "<br>" +
              "Cigar:" + d.cigar + "<br>" +
              "Map Quality: " + d.mapq + "<br>" +
              "Track: " + d.track + "<br>" +
              "Reverse Strand: " + d.readNegativeStrand)
              .style("left", (d3.event.pageX) + "px")
              .style("top", (d3.event.pageY - yOffset) + "px");
          })
          .on("mouseover", function(d) {
            readDiv[i].transition()
              .duration(200)
              .style("opacity", .9);
            readDiv[i].html(d.readName)
              .style("left", (d3.event.pageX) + "px")
              .style("top", (d3.event.pageY - yOffset) + "px");
          })
          .on("mouseout", function(d) {
            readDiv[i].transition()
            .duration(500)
            .style("opacity", 0);
          });

        // white background for arrows
        var arrowBkgds = container.selectAll(".bkgd").data(data["tracks"]);
        var bkgdsModify = arrowBkgds.transition();
        bkgdsModify
        .attr('points', function(d) {
          var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width - 1;
          var height = readTrackHeight - 2;
          var yCoord = readsHeight - readTrackHeight * (d.track + 1);
          if (d.readNegativeStrand === true) { // to the right
            var rectWidth = Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart)));
            var xCoord = rectStart + rectWidth + 1;
            return ((xCoord - height) + ' ' + yCoord + ","
                + (xCoord - height) + ' ' + (yCoord + readTrackHeight) + ","
                + (xCoord+1) + ' ' + (yCoord + readTrackHeight) + ","
                + (xCoord+1) + ' ' + yCoord + ","
                + (xCoord - height) + ' ' + yCoord);
          } else if (d.readNegativeStrand === false) { // to the left
            return ((rectStart + height) + ' ' + yCoord + ","
                + (rectStart + height) + ' ' + (yCoord + readTrackHeight) + ","
                + rectStart + ' ' + (yCoord + readTrackHeight) + ","
                + rectStart + ' ' + yCoord + ","
                + (rectStart + height) + ' ' + yCoord);
          }
          });

        var newBkgds = arrowBkgds.enter();
        newBkgds
            .append("g")
            .append("polyline")
              .attr("class", "bkgd")
              .attr('points', function(d) {
                var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width - 1;
                var height = readTrackHeight - 2;
                var yCoord = readsHeight - readTrackHeight * (d.track + 1);
                if (d.readNegativeStrand === true) { // to the right
                  var rectWidth = Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart)));
                  var xCoord = rectStart + rectWidth + 1;
                  return ((xCoord - height) + ' ' + yCoord + ","
                      + (xCoord - height) + ' ' + (yCoord + readTrackHeight) + ","
                      + (xCoord+1) + ' ' + (yCoord + readTrackHeight) + ","
                      + (xCoord+1) + ' ' + yCoord + ","
                      + (xCoord - height) + ' ' + yCoord);
                } else if (d.readNegativeStrand === false) { // to the left
                  return ((rectStart + height) + ' ' + yCoord + ","
                      + (rectStart + height) + ' ' + (yCoord + readTrackHeight) + ","
                      + rectStart + ' ' + (yCoord + readTrackHeight) + ","
                      + rectStart + ' ' + yCoord + ","
                      + (rectStart + height) + ' ' + yCoord);
                }
              }).style("fill", "white");

        var arrowHeads = container.selectAll(".arrow").data(data["tracks"]);
        var arrowModify = arrowHeads.transition();
        arrowModify
        .attr('points', function(d) {
          var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width;
          var height = readTrackHeight - 2;
          var yCoord = readsHeight - readTrackHeight * (d.track + 1);
          if (d.readNegativeStrand === true) { // to the right
            var rectWidth = Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart)));
            var xCoord = rectStart + rectWidth;
            return ((xCoord - height) + ' ' + yCoord + ","
                + (xCoord - height) + ' ' + (yCoord + height+1) + ","
                + xCoord + ' ' + (yCoord + height/2) + ","
                + (xCoord - height) + ' ' + yCoord);
          } else if (d.readNegativeStrand === false) { // to the left
            var xCoord = rectStart - 1;
            return ((xCoord + height) + ' ' + yCoord + ","
                + (xCoord + height) + ' ' + (yCoord + height + 1) + ","
                + xCoord + ' ' + (yCoord + height/2) + ","
                + (xCoord + height) + ' ' + yCoord );
          }
          });

        var newArrows = arrowHeads.enter();
        newArrows
            .append("g")
            .append("polyline")
              .attr("class", "arrow")
              .attr('points', function(d) {
                var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width;
                var height = readTrackHeight - 2;
                var yCoord = readsHeight - readTrackHeight * (d.track + 1);
                if (d.readNegativeStrand === true) { // to the right
                  var rectWidth = Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart)));
                  var xCoord = rectStart + rectWidth;
                  return ((xCoord - height) + ' ' + yCoord + ","
                      + (xCoord - height) + ' ' + (yCoord + height+1) + ","
                      + xCoord + ' ' + (yCoord + height/2) + ","
                      + (xCoord - height) + ' ' + yCoord);
                } else if (d.readNegativeStrand === false) { // to the left
                  var xCoord = rectStart - 1;
                  return ((xCoord + height) + ' ' + yCoord + ","
                      + (xCoord + height) + ' ' + (yCoord + height + 1) + ","
                      + xCoord + ' ' + (yCoord + height/2) + ","
                      + (xCoord + height) + ' ' + yCoord );
                }
              }).style("fill", function(d) {
                if (d.readNegativeStrand === true) {
                  return "red";
                } else if (d.readNegativeStrand === false) {
                  return "green";
                }
            });
        // TODO: why is this defined twice?
        numTracks = d3.max(data["matePairs"], function(d) {return d.track});
        numTracks = typeof numTracks != "undefined" ? numTracks : [];

        // Add the lines connecting read pairs
        var mateLines = container.selectAll(".readPairs").data(data["matePairs"]);
        modify = mateLines.transition();
        modify
          .attr("x1", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
          .attr("y1", (function(d) { return readsHeight - readTrackHeight * (d.track+1) + readTrackHeight/2 - 1; }))
          .attr("x2", (function(d) { return ((d.end + 1)-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
          .attr("y2", (function(d) { return readsHeight - readTrackHeight * (d.track+1) + readTrackHeight/2 - 1; }));
        newData = mateLines.enter();
        newData
          .append("g")
          .append("line")
            .attr("class", "readPairs")
            .attr("x1", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
            .attr("y1", (function(d) { return readsHeight - readTrackHeight * (d.track+1) + readTrackHeight/2 - 1; }))
            .attr("x2", (function(d) { return ((d.end + 1)-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
            .attr("y2", (function(d) { return readsHeight - readTrackHeight * (d.track+1) + readTrackHeight/2 - 1; }))
            .attr("strock-width", "1")
            .attr("stroke", "steelblue");

      if (indelCheck.checked) {
        renderIndels(data["mismatches"], data["indels"], samples[i]);
      } else {
        container.selectAll(".mismatch").remove()
      }
}

function renderIndels(mismatches, indels, sample) {
    renderIndels(mismatches, indels, sample, false)
}

function renderIndelCounts(indels, sample) {
    renderIndels(null, indels, samples, true)
}

function renderMismatches(mismatches, sample) {
    renderMismatches(mismatches, sample, false)
}

function renderMismatchCounts(mismatches, sample) {
    renderMismatches(mismatches, samples, true)
}

function renderIndels(mismatches, indels, sample, isCountData) {
  var readTrackHeight = getTrackHeight();
  var selector = "#" + sample;
  var misMatchDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  if (!isCountData) {
    if (mismatchCheck.checked) {
      renderMismatches(mismatches, sample)
    } else {
      readsSvgContainer[sample].selectAll(".mrect").remove()
    }
  }

  var misRects = readsSvgContainer[sample].selectAll(".indel").data(indels);
  var modMisRects = misRects.transition()
    .attr("x", (function(d) {
      return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", (function(d) {
        if (isCountData) return 0
        else
            return readsHeight - (readTrackHeight * (d.track+1));
    }));

  var newMisRects = misRects.enter();
  newMisRects
    .append("g")
    .append("rect")
      .attr("class", "indel")
      .attr("x", (function(d) {
        return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("y", (function(d) {
          if (isCountData) return readTrackHeight
          else
              return readsHeight - (readTrackHeight * (d.track+1));
       }))
      .attr("width", (function(d) {
        if (d.op == "I" || d.op == "D" || d.op == "N") {
          return 5;
        }
        return Math.max(1,(d.end - d.start)*(width/(viewRegEnd-viewRegStart))); }))
      .attr("height", (readTrackHeight-1))
      .attr("fill", function(d) {
        if (d.op == "I") {
          return "pink";
        } else if (d.op == "D") {
          return "black";
        } else if (d.op == "N") {
          return "gray";
        }
      })
      .on("click", function(d) {
        misMatchDiv.transition()
          .duration(200)
          .style("opacity", .9);
        misMatchDiv.html(
          "Operation: " + d.op + "<br>" +
          "Length:" + (d.end - d.start) + "<br>" +
          "Count: " + d.count )
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - yOffset) + "px");
      })
      .on("mouseover", function(d) {
        misMatchDiv.transition()
          .duration(200)
          .style("opacity", .9);
          var text;
          if (d.op == "I") {
            text = "Insertion";
          } else if (d.op == "D") {
            text = "Deletion";
          } else if (d.op == "N") {
            text = "Skipped";
          }
        misMatchDiv.html(text)
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - yOffset) + "px");
      })
      .on("mouseout", function(d) {
        misMatchDiv.transition()
        .duration(500)
        .style("opacity", 0);
      });

  var removedMisRects = misRects.exit();
  removedMisRects.remove();

}

//Render mismatching bases for cigar operator
function renderMismatches(data, sample, isCountData) {
  // Making hover box
  var readTrackHeight = getTrackHeight();
  var selector = "#" + sample;
  var misMatchDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);


  //Displays rects from the data we just calculated
  var mRects = readsSvgContainer[sample].selectAll(".mrect").data(data);
  var modifiedMRects = mRects.transition()
  modifiedMRects
    .attr("x", (function(d) {
      return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", (function(d) {
        if (isCountData) return 0
        else
            return readsHeight - (readTrackHeight * (d.track+1));
     }))
    .attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)));

  var newMRects = mRects.enter();
  newMRects
    .append("g")
    .append("rect")
    .attr("class", "mrect")
    .attr("x", (function(d) {
      return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", (function(d) {
        if (isCountData) return 0
        else
            return readsHeight - (readTrackHeight * (d.track+1))}))
    .attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)))
    .attr("height", (readTrackHeight-1))
    .attr("fill", function(d) {
      currBase = d.sequence;
      if (currBase === "G") {
        return '#00C000'; //GREEN
      } else if (currBase === "C") {
        return '#E00000'; //CRIMSON
      } else if (currBase === "A") {
        return '#5050FF'; //AZURE
      } else if (currBase === "T") {
        return '#E6E600'; //TWEETY BIRD
      } else if (currBase === "N") {
        return 'black'; //WHITE
      }
    })
    .on("click", function(d) {
      misMatchDiv.transition()
        .duration(200)
        .style("opacity", .9);
      if (isCountData) {
          var counts = d.count
          var aCount = typeof counts["A"] != "undefined" ? counts["A"] : 0;
          var tCount = typeof counts["T"] != "undefined" ? counts["T"] : 0;
          var cCount = typeof counts["C"] != "undefined" ? counts["C"] : 0;
          var gCount = typeof counts["G"] != "undefined" ? counts["G"] : 0;
          misMatchDiv.html(
              "Ref Base: " + d.refBase + "<br>" +
              "Base Counts:<br> " + "A: " + aCount + "<br>T: " + tCount + "<br>C: " + cCount + "<br>G: " + gCount)
              .style("left", (d3.event.pageX) + "px")
              .style("top", (d3.event.pageY - yOffset) + "px");
      } else {
        misMatchDiv.html(
          "Ref Base: " + d.refBase + "<br>" +
          "Base: " + d.sequence)
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - yOffset) + "px");
      }
    })
    .on("mouseover", function(d) {
        misMatchDiv.transition()
            .duration(200)
            .style("opacity", .9);
        var sequence = isCountData == true ? "" : "<br>Base: " + d.sequence;
        misMatchDiv.html(
            "Ref Base: " + d.refBase +
            sequence)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - yOffset) + "px");
    })
    .on("mouseout", function(d) {
        misMatchDiv.transition()
        .duration(500)
        .style("opacity", 0);
      });
    var removedMRects = mRects.exit();
    removedMRects.remove()
}
