var varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
//Configuration Variables
var width = window.innerWidth - 18;

// Section Heights
var refHeight = 38;
var varHeight = 10;
var freqHeight = 200;

// Svg Containers for refArea (exists is all views)
var refContainer = d3.select("#refArea")
  .append("svg")
    .attr("width", width)
    .attr("height", refHeight);


// Svg container for variant frequency
var svg = d3.select("#varFreqArea")
    .append("svg")
    .attr("width", width)
    .attr("height", freqHeight);


// Mousemove for ref containers
refContainer.on('mousemove', function () {
    var xPosition = d3.mouse(this)[0];
    d3.selectAll(".verticalLine")
      .attr({
        "x1" : xPosition,
        "x2" : xPosition
      })
});

// Create the scale for the axis
var refAxisScale = d3.scale.linear()
    .domain([viewRegStart, viewRegEnd])
    .range([0, width]);

// Create the axis
var refAxis = d3.svg.axis()
   .scale(refAxisScale);

// Add the axis to the container
refContainer.append("g")
    .attr("class", "axis")
    .call(refAxis);

//All rendering of data, and everything setting new region parameters, is done here
render(viewRefName, viewRegStart, viewRegEnd);

// Functions
function render(refName, start, end) {
  //Adding Reference rectangles
  viewRegStart = start;
  viewRegEnd = end;
  viewRefName = refName

  //Add Region Info
  d3.select("h2")
    .text("Current Region: " + viewRefName + ":"+ viewRegStart + "-" + viewRegEnd);


  renderReference();

  // Svg Containers, and vertical guidance lines and animations set here for all divs

  // Svg Containers for refArea (exists in all views)
  var refContainer = d3.select("#refArea")
    .append("svg")
      .attr("width", width)
      .attr("height", refHeight);

  // Vertical Guidance Line for refContainer
  var refVertLine = refContainer.append('line')
    .attr({
      'x1': 0,
      'y1': 0,
      'x2': 0,
      'y2': refHeight
    })
    .attr("stroke", "#002900")
    .attr("class", "verticalLine");

  // Mousemove for ref containers
  refContainer.on('mousemove', function () {
      var xPosition = d3.mouse(this)[0];
      d3.selectAll(".verticalLine")
        .attr({
          "x1" : xPosition,
          "x2" : xPosition
        })
  });

  //Variants
  if (variantsExist === true) {
    renderVariantFrequency();
    renderVariants();
  } else {
    console.log("Error: Variants not loaded");
  }
}

function renderVariants() {

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

  // Making hover box
  var varDiv = d3.select("#varArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  d3.json(varJsonLocation, function(error, alldata) {
    if (error) throw error;
    var data = alldata.variants;
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


function renderVariantFrequency() {

  // Making hover box
  var varDiv = d3.select("#varFreqArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

    var varVertLine = varDiv.append('line')
      .attr({
        'x1': 0,
        'y1': 0,
        'x2': 0,
        'y2': varHeight
      })
      .attr("stroke", "#002900")
      .attr("class", "verticalLine");

    varDiv.on('mousemove', function () {
        var xPosition = d3.mouse(this)[0];
        d3.selectAll(".verticalLine")
          .attr({
            "x1" : xPosition,
            "x2" : xPosition
          })
    });

  var margin = {top: 20, right: 0, bottom: 30, left: 0},
      height = 200 - margin.top - margin.bottom;

  // var x = d3.scale.ordinal()
  //     .rangeRoundBands([0, width], .1);
  var x = d3.scale.linear()
          .range([0, width]);

  var y = d3.scale.linear()
      .range([height, 0]);

  var xAxis = d3.svg.axis()
      .scale(x)
      .orient("bottom");

  var yAxis = d3.svg.axis()
      .scale(y)
      .orient("left")
      .ticks(10, "%");

  d3.json(varJsonLocation, function(error, alldata) {
    if (error) throw error;
    var data = alldata.frequencies;

    x.domain([viewRegStart, viewRegEnd]);
    y.domain([0, d3.max(data, function(d) { return d.count; })]);

    svg.append("g")
        .attr("class", "x axis")
        .attr("transform", "translate(0," + height + ")")
        .call(xAxis);

    svg.append("g")
        .attr("class", "y axis")
        .call(yAxis)
      .append("text")
        .attr("transform", "rotate(-90)")
        .attr("y", 6)
        .attr("dy", ".71em")
        .style("text-anchor", "end")
        .text("Frequency");

    svg.selectAll(".bar")
        .data(data)
      .enter().append("rect")
        .attr("class", "bar")
        .attr("fill", '#2E6DA4')
        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
        .attr("y", function(d) { return y(d.count); })
        .attr("height", function(d) { return height - y(d.count); })
        .on("mouseover", function(d) {
          varDiv.transition()
            .duration(200)
            .style("opacity", .9);
          varDiv.html("Samples with variant: " + d.count)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
        })
        .on("mouseout", function(d) {
          varDiv.transition()
          .duration(500)
          .style("opacity", 0);
        });
  });

}
