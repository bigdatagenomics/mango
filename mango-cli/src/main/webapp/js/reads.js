//Configuration Variables
var readsHeight = 100;
var padding = 3;

var yOffset = 200;
// svg class for alignment data
var alignmentSvgClass = "alignmentSvg";
// stores sample indel data
var indelSvgContainer = {};
// stores sample mismatch data
var readCountSvgContainer = {};
// stores sample alignment data. Contains MODIFIED samples as keys
var readAlignmentSvgContainer = {};
// Contains 1 to 1 mapping of MODIFIED sample names to RAW sample names
var sampleData = [];
//bin size
var binSize = 1;

//Manages changes when clicking checkboxes
d3.selectAll("input").on("change", checkboxChange);

for (var i = 0; i < samples.length; i++) {

  var selector = "#" + samples[i] + ">.sampleIndels";
  indelSvgContainer[samples[i]] = d3.select(selector)
    .select("svg")
      .attr("height", trackHeight)
      .attr("width", width);


    selector = "#" + samples[i] + ">.sampleSummary";
    readCountSvgContainer[samples[i]] = d3.select(selector)
      .select(".mismatch-svg")
        .attr("height", 100)
        .attr("width", width);

}

function renderMergedReads(refName, start, end) {
    startWait("#readsArea");

    // Define json location of reads data
    var readsJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end="
        + viewRegEnd + "&sample=" + sampleIds;

    // Render data for each sample
  d3.json(readsJsonLocation,function(error, json) {
    if (error != null) {
        if (error.status == 413)  // entity too large
            json.reads = "";
    }
    renderCoverage(JSON.parse(json.coverage));


    // iterate through all samples and render merged reads
    for (var i = 0; i < samples.length; i++) {

        // Zoomed out too far for resolution. print mismatch path instead.
        if(json.reads == "") {
            indelSvgContainer[samples[i]].selectAll(".indel").remove();
            readCountSvgContainer[samples[i]].selectAll("g").remove();
        } else {
            var data = JSON.parse(json.reads);
            var data = typeof data[rawSamples[i]] != "undefined" ? data[rawSamples[i]] : [];
            var selector = "#" + samples[i];
            renderd3Line(readCountSvgContainer[samples[i]], height);

            sampleData[i] = [];
            sampleData[i].mismatches = data.filter(function(d) { return d.op === "M"})
            sampleData[i].indels = data.filter(function(d) { return d.op !== "M"})

            var removed = readCountSvgContainer[samples[i]].selectAll("path").remove();
            renderMismatchCounts(sampleData[i].mismatches, samples[i]);
            renderIndelCounts(sampleData[i].indels, samples[i]);
        }
    }
    stopWait("#readsArea");
  });

    var keys = Object.keys(readAlignmentSvgContainer);
    keys.forEach(function(sample) {
        var checkSelector = "#viewAlignments" + filterName(sample);
        if ($(checkSelector).is(':checked')) {
            renderAlignments(refName, start, end, sample);
        }
    });
}

function renderAlignments(refName, start, end, sample) {
    var isData = sample in readAlignmentSvgContainer;
    if (isData) {
        var region = readAlignmentSvgContainer[sample];
        if (region.refName == refName && region.start == start && region.end == end) {
            return;
        }
    }

    readAlignmentSvgContainer[sample] = {
        refName: refName,
        start: start,
        end: end
    };

    // Define json location of reads data
    var readsJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end="
    + viewRegEnd + "&sample=" + sample + "&isRaw=true";

   d3.json(readsJsonLocation,function(error, ret) {
        if (error) return error;
   if (!isValidHttpResponse(ret)) {
    return;
   }
   var readsData = typeof ret[sample] != "undefined" ? ret[sample] : [];
      renderReadsByResolution(readsData, sample);
   });

}

// Renders reads by resolution
function renderReadsByResolution(data, rawSample) {
        var readDiv = [];
        var container = [];

        var sample = filterName(rawSample);
        var selector = getAlignmentSelector(sample);

        container = d3.select(selector)
            .select("svg")
            .attr("class", alignmentSvgClass)
            .attr("height", (readsHeight))
            .attr("width", width);


        var removed = container.selectAll("g").remove();

        // Making hover box
        readDiv[i] = d3.select(selector)
          .append("div")
          .attr("class", "tooltip")
          .style("opacity", 0);

        // Define x axis
        var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

        // Reformat data to account for emtpy Json
        data['records'] = typeof data['records'] != "undefined" ? data['records'] : [];
        data['mismatches'] = typeof data['mismatches'] != "undefined" ? data['mismatches'] : [];
        data['indels'] = typeof data['indels'] != "undefined" ? data['indels'] : [];
        data['matePairs'] = typeof data['matePairs'] != "undefined" ? data['matePairs'] : [];

        var numTracks = d3.max(data['records'], function(d) {return d.track});
        numTracks = typeof numTracks != "undefined" ? numTracks : [];

        readsHeight = (numTracks+1)*trackHeight;

        // Reset size of svg container
        container.attr("height", (readsHeight));

        //Add the rectangles
        var rects = container.selectAll(".readrect").data(data['records']);
        var modify = rects.transition();

      modify
        .attr("x", (function(d) { return xAxisScale(d.start); }))
        .attr("y", (function(d) { return readsHeight - trackHeight * (d.track+1); }))
        .attr("height", (trackHeight-1))
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

      var newData = rects.enter();
      newData
        .append("g")
        .append("rect")
        .attr("class", "readrect")
            .attr("x", (function(d) { return xAxisScale(d.start); }))
        .attr("y", (function(d) { return readsHeight - trackHeight * (d.track+1); }))
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
        .attr("height", (trackHeight-1))
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
            readDiv[i].html("Read Name: " + d.readName + "<br>" +
                                          "Start: " + d.start + "<br>" +
                                          "End: " + d.end + "<br>" +
                                          "Cigar:" + d.cigar + "<br>" +
                                          "Map Quality: " + d.mapq + "<br>" +
                                          "Track: " + d.track + "<br>" +
                                          "Reverse Strand: " + d.readNegativeStrand)
              .style("left", (d3.event.pageX) + "px")
              .style("top", (d3.event.pageY - yOffset) + "px");
          })
          .on("mouseout", function(d) {
            readDiv[i].transition()
            .duration(500)
            .style("opacity", 0);
          });

        // white background for arrows
        var arrowBkgds = container.selectAll(".bkgd").data(data['records']);
        var bkgdsModify = arrowBkgds.transition();
        bkgdsModify
        .attr('points', function(d) {
          var rectStart = xAxisScale(d.start) - 1;
          var height = trackHeight - 2;
          var yCoord = readsHeight - trackHeight * (d.track + 1);
          if (d.readNegativeStrand === true) { // to the right
            var rectWidth = Math.max(1,(d.end - d.start)*(width/(viewRegEnd-viewRegStart)));
            var xCoord = rectStart + rectWidth + 1;
            return ((xCoord - height) + ' ' + yCoord + ","
                + (xCoord - height) + ' ' + (yCoord + trackHeight) + ","
                + (xCoord+1) + ' ' + (yCoord + trackHeight) + ","
                + (xCoord+1) + ' ' + yCoord + ","
                + (xCoord - height) + ' ' + yCoord);
          } else if (d.readNegativeStrand === false) { // to the left
            return ((rectStart + height) + ' ' + yCoord + ","
                + (rectStart + height) + ' ' + (yCoord + trackHeight) + ","
                + rectStart + ' ' + (yCoord + trackHeight) + ","
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
                var rectStart = xAxisScale(d.start) - 1;
                var height = trackHeight - 2;
                var yCoord = readsHeight - trackHeight * (d.track + 1);
                if (d.readNegativeStrand === true) { // to the right
                  var rectWidth = Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart)));
                  var xCoord = rectStart + rectWidth + 1;
                  return ((xCoord - height) + ' ' + yCoord + ","
                      + (xCoord - height) + ' ' + (yCoord + trackHeight) + ","
                      + (xCoord+1) + ' ' + (yCoord + trackHeight) + ","
                      + (xCoord+1) + ' ' + yCoord + ","
                      + (xCoord - height) + ' ' + yCoord);
                } else if (d.readNegativeStrand === false) { // to the left
                  return ((rectStart + height) + ' ' + yCoord + ","
                      + (rectStart + height) + ' ' + (yCoord + trackHeight) + ","
                      + rectStart + ' ' + (yCoord + trackHeight) + ","
                      + rectStart + ' ' + yCoord + ","
                      + (rectStart + height) + ' ' + yCoord);
                }
              }).style("fill", "white");

        var arrowHeads = container.selectAll(".arrow").data(data['records']);
        var arrowModify = arrowHeads.transition();
        arrowModify
        .attr('points', function(d) {
          var rectStart = xAxisScale(d.start);
          var height = trackHeight - 2;
          var yCoord = readsHeight - trackHeight * (d.track + 1);
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
                var rectStart = xAxisScale(d.start);
                var height = trackHeight - 2;
                var yCoord = readsHeight - trackHeight * (d.track + 1);
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

        // Add the lines connecting read pairs
        var mateLines = container.selectAll(".readPairs").data(data["matePairs"]);
        modify = mateLines.transition();
        modify
          .attr("x1", (function(d) { return xAxisScale(d.start); }))
          .attr("y1", (function(d) { return readsHeight - trackHeight * (d.track+1) + trackHeight/2 - 1; }))
          .attr("x2", (function(d) { return xAxisScale(d.end); }))
          .attr("y2", (function(d) { return readsHeight - trackHeight * (d.track+1) + trackHeight/2 - 1; }));
        newData = mateLines.enter();
        newData
          .append("g")
          .append("line")
            .attr("class", "readPairs")
            .attr("x1", (function(d) { return xAxisScale(d.start); }))
            .attr("y1", (function(d) { return readsHeight - trackHeight * (d.track+1) + trackHeight/2 - 1; }))
            .attr("x2", (function(d) { return xAxisScale(d.end); }))
            .attr("y2", (function(d) { return readsHeight - trackHeight * (d.track+1) + trackHeight/2 - 1; }))
            .attr("strock-width", "1")
            .attr("stroke", "steelblue");

        renderAlignmentIndels(data["indels"], sample);
        renderAlignmentMismatches(data["mismatches"], sample);

        // check if mismatches and indels are currently displayed, and show/hide accordingly
        checkboxChange();

        renderd3Line(container, readsHeight);
}

function formatIndelText(op, object) {
    var text = "";
    if (op == "I" && getIndelCounts("I", object) > 0) {
    text += "<p style='color:pink'>Insertions: </br>";
    for (var sequence in object) {
        if (object.hasOwnProperty(sequence)) {
            text = text + (sequence + ": " + object[sequence]) + "</br>"
        }
    }
    text = text + "</p>"
    } else if (op == "D" && getIndelCounts("D", object) > 0) {
        text += "<p style='color:black'>Deletions: </br>";
        for (var length in object) {
            if (object.hasOwnProperty(length)) {
                text = text + (Array(parseInt(length)+1).join("N") + ": " + object[length]) + "</br>"
            }
        }
        text = text + "</p>"
    }
    return text;
}

function getIndelCounts(op, object) {
    count = 0;
    if (op == "I") {
    for (var sequence in object) {
        if (object.hasOwnProperty(sequence)) {
            count += object[sequence];
        }
    }
    } else if (op == "D") {
        for (var length in object) {
            if (object.hasOwnProperty(length)) {
                count += object[length];
            }
        }
    }
    return count;
}

function renderIndelCounts(indels, sample) {
  var selector = "#" + sample;
  var misMatchDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  var range = viewRegEnd-viewRegStart;

  var misRects = indelSvgContainer[sample].selectAll(".indel").data(indels);

  var modMisRects = misRects.transition();

  // Define x axis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

    modMisRects
      .attr("transform", function(d) { return "translate(" + xAxisScale(d.refCurr) + ",0)"; });

    modMisRects
    .attr("width", (function(d) {
        return Math.max(1,(binSize * width/(viewRegEnd-viewRegStart))); }))
    .attr("fill", function(d) {
        var is = getIndelCounts("I", d.count.I);
        var ds = getIndelCounts("D", d.count.D);
        if (is > 0 && ds == 0) {
            return "pink"
        } else if (ds > 0 && is == 0) {
            return "black";
        } else { // both indels and deletions
            return "#7D585F"; // mix of black and pink
        }
    });

  // new rectangles on initial page render
  var newMisRects = misRects
    .enter().append("rect")
    .attr("class", "indel")
    .attr("y", (function(d) {
        return padding;
     }))
    .attr("width", (function(d) {
      return Math.max(1,(binSize * width/(viewRegEnd-viewRegStart))); }))
    .attr("height", (trackHeight-1))
    .attr("fill", function(d) {
        var is = getIndelCounts("I", d.count.I);
        var ds = getIndelCounts("D", d.count.D);
        if (is > 0 && ds == 0) {
            return "pink"
        } else if (ds > 0 && is == 0) {
            return "black";
        } else { // both indels and deletions
            return "#7D585F";
        }
    })
        .attr("transform", function(d) {
        return "translate(" + xAxisScale(d.refCurr) + ",0)"; })
      .on("click", function(d) {
        misMatchDiv.transition()
          .duration(200)
          .style("opacity", .9);
        misMatchDiv.html(
          formatIndelText("I", d.count.I) +
          formatIndelText("D", d.count.D))
          .style("text-align", "left")
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - yOffset) + "px");
      })
      .on("mouseover", function(d) {
        misMatchDiv.transition()
          .duration(200)
          .style("opacity", .9);
        var text = "<p style='color:pink'>Insertions: " + getIndelCounts("I", d.count.I) +
        "</p><p style='color:black'>Deletions: "+ getIndelCounts("D", d.count.D) + "</p>";
        misMatchDiv.html(text)
          .style("text-align", "left")
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

function renderAlignmentIndels(indels, sample) {
  var selector = "#" + sample;
  var misMatchDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  var misRects = d3.select(getAlignmentSelector(sample) + ">." + alignmentSvgClass).selectAll(".indel").data(indels);

  // Define x axis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

  var modMisRects = misRects.transition()
    .attr("x", (function(d) {
      return xAxisScale(d.refCurr); }))
    .attr("y", (function(d) {
        return 0;
    }))
    .attr("width", (function(d) {
        return Math.max(1,(d.length)*(width/(viewRegEnd-viewRegStart))); }))
    .attr("fill", function(d) {
       if (d.op == "I") {
         return "pink";
       } else if (d.op == "D") {
         return "black";
       } else if (d.op == "N") {
         return "gray";
       }
     });

  var newMisRects = misRects.enter();
  newMisRects
    .append("g")
    .append("rect")
      .attr("class", "indel")
      .attr("x", (function(d) {
        return xAxisScale(d.refCurr); }))
      .attr("y", (function(d) {
          return readsHeight - (trackHeight * (d.track+1));
       }))
      .attr("width", (function(d) {
        return Math.max(1,(d.length)*(width/(viewRegEnd-viewRegStart))); }))
      .attr("height", (trackHeight-1))
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
          "Length:" + (d.length) + "<br>" +
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
function renderMismatchCounts(data, sample) {
    // Making hover box
    var selector = "#" + sample;
    var misMatchDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);


    data.forEach(function(d) {
        d["A"] = typeof d.count["A"] != "undefined" ? d.count["A"] : 0;
        d["T"] = typeof d.count["T"] != "undefined" ? d.count["T"] : 0;
        d["C"] = typeof d.count["C"] != "undefined" ? d.count["C"] : 0;
        d["G"] = typeof d.count["G"] != "undefined" ? d.count["G"] : 0;
    });

    data.forEach(function(d) {
        var y0 = 0;
        d.totals = [];
        for (var base in baseColors) {
           if (baseColors.hasOwnProperty(base)) {
            d.totals.push({base: base, y0: y0, y1: y0 += +d[base], refCurr: d["refCurr"]});
           }
        }
        d.sum = d.totals[d.totals.length - 1].y1;
    });
    
    var y = d3.scale.linear()
        .rangeRound([mismatchHeight, 0]);
    y.domain([0, d3.max(data, function(d) { return d.sum; })]);

    var yAxis = d3.svg.axis()
              .scale(y)
              .orient("left")
              .ticks(10);
    

     // Define x axis
     var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

    //Displays rects from the data we just calculated
    var mRects = readCountSvgContainer[sample].selectAll("g").data(data);
    var coloredRects = mRects.selectAll(".mrect").data(function(d) {return d.totals});


  // modified rectangles on update
  var modifiedMRects = mRects.transition();
  var modifiedColoredRects = coloredRects.transition();

  modifiedMRects
    .attr("transform", function(d) { return "translate(" + xAxisScale(d.refCurr) + ",0)"; });

  modifiedColoredRects
    .attr("y", (function(d) { return y(d.y1); }))
    .attr("width", Math.max(1, binSize * width/(viewRegEnd-viewRegStart)))
    .attr("height", function(d) { return y(d.y0) - y(d.y1); })
    .attr("fill", function(d) {return baseColors[d.base]; });

  // new rectangles on initial page render
  var newMRects = mRects
        .enter().append("g")
        .attr("transform", function(d) { return "translate(" + xAxisScale(d.refCurr) + ",0)"; });

  newMRects.selectAll("rect")
    .append("rect")
        .data(function(d) { return d.totals; })
        .enter().append("rect")
        .attr("class", "mrect")
        .attr("y", (function(d) { return y(d.y1); }))
        .attr("width", Math.max(1, binSize * width/(viewRegEnd-viewRegStart)))
        .attr("height", function(d) { return y(d.y0) - y(d.y1); })
        .attr("fill", function(d) {return baseColors[d.base]; });

    newMRects
    .on("click", function(d) {
      misMatchDiv.transition()
        .duration(200)
        .style("opacity", .9);
      misMatchDiv.html(
          "Ref Base: " + d.refBase + "<br>"
          + "Base Counts:" +
            "<p style='color:" + baseColors["A"] + "'>A: " + d["A"] + "</p>" +
            "<p style='color:" + baseColors["T"] + "'>T: " + d["T"] + "</p>" +
            "<p style='color:" + baseColors["C"] + "'>C: " + d["C"] + "</p>" +
            "<p style='color:" + baseColors["G"] + "'>G: " + d["G"] + "</p>")
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY) + "px");
    })
    .on("mouseover", function(d) {
        misMatchDiv.transition()
            .duration(200)
            .style("opacity", .9);
        misMatchDiv.html(
            "Ref Base: " + d.refBase)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY) + "px");
    })
    .on("mouseout", function(d) {
        misMatchDiv.transition()
        .duration(500)
        .style("opacity", 0);
     });

    var removedMRects = mRects.exit();
    removedMRects.remove()

    var svg = d3.select(selector).select(".mismatch-svg")
          .append("svg")
            .attr("width", "100%")
            .attr("height", 110)
          .append("g")
            .attr("transform", "translate(" + parseInt(width-30) + "," + -0.5 + ")");
    
    svg.append("g")
        .attr("class", "y axis")
        .call(yAxis)
      .append("text")
        // .attr("transform", "translate(0,10)")
        .attr("transform", "rotate(-90)")
        .attr("x",2)
        .attr("y", 8)
        .attr("dy", ".70em")
        .style("text-anchor", "end")
        .text("Mismatch Frequency");
        

}

//Render mismatching bases for cigar operator
function renderAlignmentMismatches(data, sample) {
  // Making hover box
  var selector = "#" + sample;
  var misMatchDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  //Displays rects from the data we just calculated
  var mRects = d3.select(getAlignmentSelector(sample) + ">." + alignmentSvgClass).selectAll(".mrect").data(data);

  // Define x axis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

  // modified rectangles on update
  var modifiedMRects = mRects.transition();
  modifiedMRects
    .attr("x", (function(d) {
      return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", function(d) { return d.refCurr})
    .attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)));

  var newMRects = mRects.enter();
  newMRects
    .append("g")
        .attr("transform", function(d) { return "translate(" + xAxisScale(d.refCurr) + ",0)"; })
        .append("rect")
        .attr("class", "mrect")
        .attr("y", (function(d) {
            return readsHeight - (trackHeight * (d.track+1));
        })).attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)))
        .attr("height", function(d) {
            return (trackHeight-1);
        }).attr("fill", function(d) {
              currBase = d.sequence;
              if (currBase === "N") {
                return nColor;
              } else {
                return baseColors[currBase];
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
            .style("top", (d3.event.pageY) + "px");
        })
        .on("mouseover", function(d) {
          misMatchDiv.transition()
            .duration(200)
            .style("opacity", .9);
          misMatchDiv.html(
            "Ref Base: " + d.refBase + "<br>" +
            "Base: " + d.sequence)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY) + "px");
        })
        .on("mouseout", function(d) {
            misMatchDiv.transition()
            .duration(500)
            .style("opacity", 0);
          });

    var removedMRects = mRects.exit();
    removedMRects.remove()

}
