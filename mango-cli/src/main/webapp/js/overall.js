//Configuration Variables
var base = 50;
var trackHeight = 6;

// Section Heights
var refHeight = 38;
var featHeight = 10;
var varHeight = 10;
var readsHeight = 0; //Default variable: this will change based on number of reads

// Global Data
var refSequence;
var sampleData;
var sDict;
//Manages changes when clicking checkboxes
d3.selectAll("input").on("change", checkboxChange);

// Create the scale for the axis
var refAxisScale = d3.scale.linear()
    .domain([viewRegStart, viewRegEnd])
    .range([0, width]);

// Create the axis
var refAxis = d3.svg.axis()
   .scale(refAxisScale);

if (featuresExist === true) {
  var featureSvgContainer = d3.select("#featArea")
    .append("svg")
      .attr("height", featHeight)
      .attr("width", width);

  renderd3Line(featureSvgContainer, featHeight);
}

// send pixel size for bining and initialize autocomplete
var initJson =  "/init/" + Math.round($(".samples").width());
d3.json(initJson, function(error, seqDict) {
  sDict =seqDict;
  autoComplete(seqDict);
});

//All rendering of data, and everything setting new region parameters, is done here
render(viewRefName, viewRegStart, viewRegEnd);


// Functions
function render(refName, start, end, mapQuality) {

  //Updating Search Bar 
  document.getElementById("autocomplete").value = refName+":"+start.toString()+"-"+end.toString();
  
  //Adding Reference rectangles
  setGlobalReferenceRegion(refName, start, end);
  setGlobalMapQ(mapQuality);

  //Add Region Info
  var placeholder = viewRefName + ":"+ viewRegStart + "-" + viewRegEnd;
  $('#regInput').attr('placeholder', placeholder);
  saveRegion(refName, start, end);

  // Reference
  renderReference(refName, start, end);

  // Features
  if (featuresExist) {
    renderFeatures(refName, start, end);
  }

  // Variants
  if (variantsExist) {
    renderVariants(refName, start, end);
  }

  // Reads and Coverage
  if (readsExist) {
    // Disable alignments if region is too big

    // hide reads and disable checkbox
    if (end - start > 5000) {
      $(".viewAlignments").attr("checked", false);
      $(".alignmentData").hide();

      $(".viewAlignments").prop("disabled", true)
    } else $(".viewAlignments").prop("disabled", false)
    renderMergedReads(refName, start, end, mapQuality);
  }
}

function saveRegion(viewRefName, viewRegStart, viewRegEnd) {
  var saveJsonLocation = "/viewregion/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  d3.json(saveJsonLocation, function(error, data) {});
}

function renderFeatures(viewRefName, viewRegStart, viewRegEnd) {

  var featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  // define x axis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

  // Making hover box
  var featDiv = d3.select("#featArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  d3.json(featureJsonLocation, function(error, data) {
  if (error) return error;
  if (!isValidHttpResponse(data)) {
    return;
  }
    var rects = featureSvgContainer.selectAll("rect").data(data);
    var modify = rects.transition();
    modify
      .attr("x", (function(d) { return xAxisScale(d.start); }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

    var newData = rects.enter();
    newData
      .append("g")
      .append("rect")
        .attr("x", (function(d) { return xAxisScale(d.start); }))
        .attr("y", 0)
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
        .attr("height", featHeight)
        .attr("fill", "#6600CC")
        .on("click", function(d) {
          featDiv.transition()
            .duration(200)
            .style("opacity", .9);
          featDiv.html(
            "Feature Id: " + d.featureId + "<br>" +
            "Feature Type: " + d.featureType + "<br>" +
            "Start: " + d.start + "<br>" +
            "End: " + d.end)
            .style("left", (d3.event.pageX - 200) + "px")
            .style("top", (d3.event.pageY - 200) + "px");
        })
        .on("mouseover", function(d) {
          featDiv.transition()
          .duration(200)
          .style("opacity", .9);
          featDiv.html(d.featureId)
          .style("left", (d3.event.pageX - 200) + "px")
          .style("top", (d3.event.pageY - 200) + "px");
        })
        .on("mouseout", function(d) {
          featDiv.transition()
          .duration(500)
          .style("opacity", 0);
        });

      var removed = rects.exit();
      removed.remove();
    });
}
