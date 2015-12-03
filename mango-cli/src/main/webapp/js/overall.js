var varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
//Configuration Variables
var width = window.innerWidth - 18;
var base = 50;
var trackHeight = 6;

// Section Heights
var refHeight = 38;
var featHeight = 10;
var varHeight = 10;
var readsHeight = 0; //Default variable: this will change based on number of reads

// Global Data
var refSequence;
var readsData;

function checkboxChange() {
  for (var i = 0; i < samples.length; i++) {
    if (indelCheck.checked) {
      renderMismatches(readsData[i], samples[i]);
    } else {
      readsSvgContainer[samples[i]].selectAll(".mismatch").remove();
    }
  }
}

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

  var featVertLine = featureSvgContainer.append('line')
    .attr({
      'x1': 0,
      'y1': 0,
      'x2': 0,
      'y2': featHeight
    })
    .attr("stroke", "#002900")
    .attr("class", "verticalLine");

  featureSvgContainer.on('mousemove', function () {
    var xPosition = d3.mouse(this)[0];
    d3.selectAll(".verticalLine")
      .attr({
        "x1" : xPosition,
        "x2" : xPosition
      })
  });

}

if (variantsExist === true) {
  var varSvgContainer = d3.select("#varArea")
    .append("svg")
      .attr("width", width)
      .attr("height", varHeight);

  var varVertLine = varSvgContainer.append('line')
    .attr({
      'x1': 0,
      'y1': 0,
      'x2': 0,
      'y2': varHeight
    })
    .attr("stroke", "#002900")
    .attr("class", "verticalLine");

  varSvgContainer.on('mousemove', function () {
      var xPosition = d3.mouse(this)[0];
      d3.selectAll(".verticalLine")
        .attr({
          "x1" : xPosition,
          "x2" : xPosition
        })
  });
}

//All rendering of data, and everything setting new region parameters, is done here
render(viewRefName, viewRegStart, viewRegEnd);

// Functions
function render(refName, start, end) {
  //Adding Reference rectangles
  viewRegStart = start;
  viewRegEnd = end;
  viewRefName = refName

  //Add Region Info
  var placeholder = viewRefName + ":"+ viewRegStart + "-" + viewRegEnd;
  $('#regInput').attr('placeholder', placeholder);

  referenceStringLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  // Features
  if (featuresExist === true) {
    renderFeatures();
  }

  //Variants
  if (variantsExist === true) {
    renderVariants();
  }


}

function renderFeatures() {

  // Making hover box
  var featDiv = d3.select("#featArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  d3.json(featureJsonLocation, function(error, data) {
    // Add the rectangles
    var rects = featureSvgContainer.selectAll("rect").data(data);

    var modify = rects.transition();
    modify
      .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

    var newData = rects.enter();
    newData
      .append("g")
      .append("rect")
        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
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
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
        })
        .on("mouseover", function(d) {
          featDiv.transition()
          .duration(200)
          .style("opacity", .9);
          featDiv.html(d.featureId)
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
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

function renderVariants() {

  // Making hover box
  var varDiv = d3.select("#varArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  d3.json(varJsonLocation, function(error, data) {

    // Add the rectangles
    var rects = varSvgContainer.selectAll("rect").data(data);

    var modify = rects.transition();
    modify
      .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

    var newData = rects.enter();
    newData
      .append("g")
      .append("rect")
        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
        .attr("y", 0)
        .attr("fill", function(d) {
          if (d.alleles === "Ref / Alt") {
            return '#00FFFF'; //CYAN
          } else if (d.alleles === "Alt / Alt") {
            return '#FF66FF'; //MAGENTA
          } else if (d.reference === "Ref / Ref") {
            return '#99FF33'; //NEON GREEN
          } else {
            return '#FFFF66'; //YELLOW
          }
        })
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
        .attr("height", varHeight)
        .on("click", function(d) {
          varDiv.transition()
            .duration(200)
            .style("opacity", .9);
          varDiv.html(
            "Contig: " + d.contigName + "<br>" +
            "Alleles: " + d.alleles)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
        })
        .on("mouseover", function(d) {
          varDiv.transition()
            .duration(200)
            .style("opacity", .9);
          varDiv.html(d.alleles)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
        })
        .on("mouseout", function(d) {
          varDiv.transition()
          .duration(500)
          .style("opacity", 0);
        });

    var removed = rects.exit();
    removed.remove();
  });
}
