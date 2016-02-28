//Configuration Variables
var readsHeight = 0; //Default variable: this will change based on number of reads
var fileSelector = "col-md-2";
var readsSelector = "col-md-10";
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
  $("#readsArea").append("<div id=\"" + samples[i] + "\" class=\"row samples resize-vertical\"></div>");
  $("#"+samples[i]).append("<div class=\"" + fileSelector +" \"></div>");
  $("#"+samples[i]).append("<div class=\"" + readsSelector + " graphArea\"></div>");
  $("#"+samples[i] + ">.col-md-10").append("<div class=\"sampleCoverage\"></div>");
  $("#"+samples[i] + ">.col-md-10").append("<div class=\"sampleReads\"></div>");
  var width = $(".sampleReads").width();

  var selector = "#" + samples[i] + ">.col-md-10>.sampleReads";
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

// Functions
function renderReads(refName, start, end, quality) {
    quality = quality || 0
    //Adding Reference rectangles
    viewRegStart = start;
    viewRegEnd = end;
    viewRefName = refName;

    var readsJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd + "&sample=" + sampleId
        + "&quality=" + quality;
    renderJsonReads(readsJsonLocation);


}

function renderJsonReads(readsJsonLocation) {

  d3.json(readsJsonLocation,function(error, ret) {
      for (var i = 0; i < samples.length; i++) {
        if (!jQuery.isEmptyObject(ret)) {
          // render reads for low or high resolution depending on base range
          if (viewRegEnd - viewRegStart > 100) {
            renderReadsByResolution(false, ret[rawSamples[i]], samples[i], i);
          } else {
            renderReadsByResolution(true,ret[rawSamples[i]], samples[i], i);
          }
        } else {
          console.log("no reads to display");
        }
      }

  });
}

// Renders reads by resolution
function renderReadsByResolution(isHighRes, data, sample, i) {

        var readTrackHeight = getTrackHeight();
        var readDiv = [];

        // Making hover box
        readDiv[i] = d3.select("#" + samples[i])
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
        readsSvgContainer[sample].select(".axis").remove();

        var selector = "#" + sample;
        sampleData[i] = [];
        sampleData[i].reads = data['tracks'];
        sampleData[i].mismatches = data['mismatches'];
        sampleData[i].indels = data['indels'];
        sampleData[i].pairs = data['matePairs'];

        // Renders Reads Frequency
        renderJsonCoverage(data['freq'], i)
        
        // print file name
        // TODO: this should not be redrawn every page load
        $("#" + samples[i] + ">." + fileSelector + ">.fixed-title").remove();
        var filename = data['filename'];
        $("#" + samples[i] + ">." + fileSelector).append("<div class='fixed-title'>" + filename + "</div>");

        var numTracks = d3.max(sampleData[i].reads, function(d) {return d.track});
        readsHeight = (numTracks+1)*readTrackHeight;

        // Reset size of svg container
        readsSvgContainer[sample].attr("height", (readsHeight));

        // Add the axis to the container
        readsSvgContainer[sample].append("g")
          .attr("class", "axis")
          .attr("transform", "translate(0, " + readsHeight + ")")
          .call(readsAxis);

        // Update height of vertical guide line
        $(".verticalLine").attr("y2", readsHeight);

        //Add the rectangles
        var rects = readsSvgContainer[sample].selectAll(".readrect").data(sampleData[i].reads);
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

        var removed = rects.exit();
        removed.remove();

        // white background for arrows
        var arrowBkgds = readsSvgContainer[sample].selectAll(".bkgd").data(sampleData[i].reads);
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

        var removedBkgds = arrowBkgds.exit();
        removedBkgds.remove();

        var arrowHeads = readsSvgContainer[sample].selectAll(".arrow").data(sampleData[i].reads);
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

        var removedArrows = arrowHeads.exit();
        removedArrows.remove();

        numTracks = d3.max(sampleData[i].pairs, function(d) {return d.track});

        // Add the lines connecting read pairs
        var mateLines = readsSvgContainer[samples[i]].selectAll(".readPairs").data(sampleData[i].pairs);
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

      var removedGroupPairs = mateLines.exit();
      removedGroupPairs.remove();

      if (indelCheck.checked) {
        renderMismatches(sampleData[i].mismatches, sampleData[i].indels, samples[i]);
      } else {
        readsSvgContainer[sample].selectAll(".mismatch").remove()
      }
}


function renderMismatches(mismatches, indels, sample) {
  var readTrackHeight = getTrackHeight();
  var selector = "#" + sample;
  var misMatchDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  //Display M: This is where we compare mismatching pairs
  if (mismatchCheck.checked) {
    renderMCigar(mismatches, sample)
  } else {
    readsSvgContainer[sample].selectAll(".mrect").remove()
  }

  var misRects = readsSvgContainer[sample].selectAll(".mismatch").data(indels);
  var modMisRects = misRects.transition()
    .attr("x", (function(d) {
      return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", (function(d) { return readsHeight - (readTrackHeight * (d.track+1)); }));

  var newMisRects = misRects.enter();
  newMisRects
    .append("g")
    .append("rect")
      .attr("class", "mismatch")
      .attr("x", (function(d) {
        return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("y", (function(d) { return readsHeight - (readTrackHeight * (d.track+1)); }))
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
          "Ref Start: " + d.refCurr + "<br>" +
          "Read Start: " + d.start + "<br>" +
          "Length:" + (d.end - d.start) + "<br>" +
          "Sequence: " + d.sequence + "<br>" +
          "Track: " + d.track)
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
function renderMCigar(data, sample) {
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
    .attr("y", function(d) { return d.refCurr})
    .attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)));

  var newMRects = mRects.enter();
  newMRects
    .append("g")
    .append("rect")
    .attr("class", "mrect")
    .attr("x", (function(d) {
      return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", function(d) { return readsHeight - (readTrackHeight * (d.track+1));})
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
      misMatchDiv.html(
        "Ref Base: " + d.refBase + "<br>" +
        "Base: " + d.sequence)
        .style("left", (d3.event.pageX) + "px")
        .style("top", (d3.event.pageY - yOffset) + "px");
    })
    .on("mouseover", function(d) {
      misMatchDiv.transition()
        .duration(200)
        .style("opacity", .9);
      misMatchDiv.html(
        "Ref Base: " + d.refBase + "<br>" +
        "Base: " + d.sequence)
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
