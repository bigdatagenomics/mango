//Configuration Variables
var readsHeight = 100;
var padding = 3;
var readTrackHeight = getTrackHeight();
var mismatchHeight = readsHeight - readTrackHeight

var yOffset = 200;
// svg class for alignment data
var alignmentSvgClass = "alignmentSvg";
// stores sample mismatch and indel data
var readCountSvgContainer = {};
// stores sample alignment data. Contains MODIFIED samples as keys
var readAlignmentSvgContainer = {};
// Contains 1 to 1 mapping of MODIFIED sample names to RAW sample names
var sampleData = [];
// bin size
var binSize = 1;

function getAlignmentSelector(sample) {
    return selector = "#" + sample + ">.alignmentData";
}

//Manages changes when clicking checkboxes
d3.selectAll("input").on("change", checkboxChange);

for (var i = 0; i < samples.length; i++) {
  $("#readsArea").append("<div id=\"" + samples[i] + "\" class=\"samples resize-vertical\"></div>");
  $("#"+samples[i]).append("<div class=\"sampleLegend\"></div>");
  $("#"+samples[i]).append("<div class=\"sampleCoverage\"></div>");
  $("#"+samples[i]).append("<div class=\"mergedReads\"></div>");
  $("#"+samples[i]).append("<div class=\"alignmentData\"></div>");

  var width = $(".mergedReads").width();

  var selector = "#" + samples[i] + ">.mergedReads";
  readCountSvgContainer[samples[i]] = d3.select(selector)
    .append("svg")
      .attr("height", (readsHeight))
      .attr("width", width);

  renderd3Line(readCountSvgContainer[samples[i]], readsHeight);

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
        var alignmentSelector = $( "#" + samples[i] + ">.alignmentData");
        $(alignmentSelector).hide();


    }
}

function toggleAlignments(sample) {
    var alignmentSelector =  $("#" + sample + ">.alignmentData");
    if (!alignmentSelector.is(':visible')) {
       renderAlignments(viewRefName, viewRegStart, viewRegEnd, mapQuality, sample);
    }
   $(alignmentSelector).slideToggle( "slow" );
}

function renderMergedReads(refName, start, end, quality) {
    renderReads(refName, start, end, quality, true)
}

function renderAlignments(refName, start, end, quality, sample) {

    var isData = sample in readAlignmentSvgContainer;
    if (isData) {
        var region = readAlignmentSvgContainer[sample];
        if (region.refName != refName || region.start !=start || region.end != end) {
            renderReads(refName, start, end, quality, false, sampleMap[sample]);

            readAlignmentSvgContainer[sample] = {
                                                    refName: refName,
                                                    start: start,
                                                    end: end
                                                };
        }

    } else {
        renderReads(refName, start, end, quality, false, sampleMap[sample]);

        readAlignmentSvgContainer[sample] = {
                                                refName: refName,
                                                start: start,
                                                end: end
                                            };
    }
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

    if (isCountData) {
        renderJsonMergedReads(readsJsonLocation);
        var keys = Object.keys(readAlignmentSvgContainer)
        keys.forEach(function(sample) {
            renderAlignments(refName, start, end, quality, sample);
        });
    } else
        renderJsonReads(readsJsonLocation, Array(samples));
}

function renderJsonMergedReads(readsJsonLocation) {

  d3.json(readsJsonLocation,function(error, ret) {
    if(error) console.log(error);
    if (!isValidHttpResponse(ret)) {
      return;
    }

    for (var i = 0; i < samples.length; i++) {
        var data = typeof ret[rawSamples[i]] != "undefined" ? ret[rawSamples[i]] : [];
        var selector = "#" + samples[i];
        sampleData[i] = [];
        sampleData[i].mismatches = typeof data['mismatches'] != "undefined" ? data['mismatches'] : [];
        sampleData[i].indels = typeof data['indels'] != "undefined" ? data['indels'] : [];
        binSize = typeof data['binSize'] != "undefined" ? data['binSize'] : 1;

        renderSamplename(i);
        renderJsonCoverage(data['freq'], i);
        renderMismatchCounts(sampleData[i].mismatches, samples[i]);
        renderIndelCounts(sampleData[i].indels, samples[i]);
    }
  });
}

function renderJsonReads(readsJsonLocation, samples) {

  d3.json(readsJsonLocation,function(error, ret) {
  if (error) return error;
  if (!isValidHttpResponse(ret)) {
    return;
  }
  for (var i = 0; i < samples.length; i++) {
      var readsData = typeof ret[samples[i]] != "undefined" ? ret[samples[i]] : [];
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
        var readDiv = [];
        var container = [];

        var sample = filterName(rawSample);
        var selector = getAlignmentSelector(sample)

        // check whether alignment container for this sample was already rendered
        if ($(selector + ">." + alignmentSvgClass).length == 0) {
            container = d3.select(selector)
                .append("svg")
                .attr("class", alignmentSvgClass)
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

        var selector = "#" + sample;
        data['tracks'] = typeof data['tracks'] != "undefined" ? data['tracks'] : [];
        data['mismatches'] = typeof data['mismatches'] != "undefined" ? data['mismatches'] : [];
        data['indels'] = typeof data['indels'] != "undefined" ? data['indels'] : [];
        data['matePairs'] = typeof data['matePairs'] != "undefined" ? data['matePairs'] : [];

        renderSamplename(i);
        var numTracks = d3.max(data["tracks"], function(d) {return d.track});
        numTracks = typeof numTracks != "undefined" ? numTracks : [];

        readsHeight = (numTracks+1)*readTrackHeight;

        // Reset size of svg container
        container.attr("height", (readsHeight));

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
        var arrowBkgds = container.selectAll(".bkgd").data(data["tracks"]);
        var bkgdsModify = arrowBkgds.transition();
        bkgdsModify
        .attr('points', function(d) {
          var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width - 1;
          var height = readTrackHeight - 2;
          var yCoord = readsHeight - readTrackHeight * (d.track + 1);
          if (d.readNegativeStrand === true) { // to the right
            var rectWidth = Math.max(1,(d.end - d.start)*(width/(viewRegEnd-viewRegStart)));
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

        renderAlignmentIndels(data["indels"], sample);
        renderAlignmentMismatches(data["mismatches"], sample);

        // check if mismatches and indels are currently displayed, and show/hide accordingly
        checkboxChange();

        renderd3Line(container, readsHeight);
}

function formatIndelText(op, object) {
    var text = "";
    if (op == "I" && getIndelCounts("I", object) > 0) {
    text += "<p style='color:pink'>Insertions: </br>"
    for (var sequence in object) {
        if (object.hasOwnProperty(sequence)) {
            text = text + (sequence + ": " + object[sequence]) + "</br>"
        }
    }
    text = text + "</p>"
    } else if (op == "D" && getIndelCounts("D", object) > 0) {
        text += "<p style='color:black'>Deletions: </br>"
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

  var misRects = readCountSvgContainer[sample].selectAll(".indel").data(indels);

  var modMisRects = misRects.transition();

    modMisRects
      .attr("transform", function(d) { return "translate(" + (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width + ",0)"; });

    modMisRects
    .attr("y", (function(d) {
        return mismatchHeight;
    }))
    .attr("width", (function(d) {
        return Math.max(1,(binSize * width/(viewRegEnd-viewRegStart))); }))
    .attr("fill", function(d) {
        var is = getIndelCounts("I", d.count.I)
        var ds = getIndelCounts("D", d.count.D)
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
        return mismatchHeight;
     }))
    .attr("width", (function(d) {
      return Math.max(1,(binSize * width/(viewRegEnd-viewRegStart))); }))
    .attr("height", (readTrackHeight-1))
    .attr("fill", function(d) {
        var is = getIndelCounts("I", d.count.I)
        var ds = getIndelCounts("D", d.count.D)
        if (is > 0 && ds == 0) {
            return "pink"
        } else if (ds > 0 && is == 0) {
            return "black";
        } else { // both indels and deletions
            return "#7D585F";
        }
    })
        .attr("transform", function(d) {
        return "translate(" + (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width + ",0)"; })
      .on("click", function(d) {
        misMatchDiv.transition()
          .duration(200)
          .style("opacity", .9);
        misMatchDiv.html(
          formatIndelText("I", d.count.I) + + d.refCurr +
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
        "</p><p style='color:black'>Deletions: "+ getIndelCounts("D", d.count.D) + "</p>" + d.refCurr ;
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

  var modMisRects = misRects.transition()
    .attr("x", (function(d) {
      return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", (function(d) {
        return readsHeight - (readTrackHeight * (d.track+1));
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
        return (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("y", (function(d) {
          return readsHeight - (readTrackHeight * (d.track+1));
       }))
      .attr("width", (function(d) {
        return Math.max(1,(d.length)*(width/(viewRegEnd-viewRegStart))); }))
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
        .rangeRound([mismatchHeight - padding, 0]);
     y.domain([0, d3.max(data, function(d) { return d.sum; })]);

  //Displays rects from the data we just calculated
    var mRects = readCountSvgContainer[sample].selectAll("g").data(data);
    var coloredRects = mRects.selectAll(".mrect").data(function(d) {return d.totals});


  // modified rectangles on update
  var modifiedMRects = mRects.transition();
  var modifiedColoredRects = coloredRects.transition();

  modifiedMRects
    .attr("transform", function(d) { return "translate(" + (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width + ",0)"; });

  modifiedColoredRects
    .attr("y", (function(d) { return y(d.y1); }))
    .attr("width", Math.max(1, binSize * width/(viewRegEnd-viewRegStart)))
    .attr("height", function(d) { return y(d.y0) - y(d.y1); })
    .attr("fill", function(d) {return baseColors[d.base]; });

  // new rectangles on initial page render
  var newMRects = mRects
        .enter().append("g")
        .attr("transform", function(d) { return "translate(" + (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width + ",0)"; });

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
          .style("top", (d3.event.pageY - yOffset) + "px");
    })
    .on("mouseover", function(d) {
        misMatchDiv.transition()
            .duration(200)
            .style("opacity", .9);
        misMatchDiv.html(
            "Ref Base: " + d.refBase)
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
        .attr("transform", function(d) { return "translate(" + (d.refCurr-viewRegStart)/(viewRegEnd-viewRegStart) * width + ",0)"; })
        .append("rect")
        .attr("class", "mrect")
        .attr("y", (function(d) {
            return readsHeight - (readTrackHeight * (d.track+1));
        })).attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)))
        .attr("height", function(d) {
            return (readTrackHeight-1);
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
            .style("top", (d3.event.pageY - yOffset) + "px");
        })
        .on("mouseover", function(d) {
            console.log(d);
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
