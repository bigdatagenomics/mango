var readJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var referenceStringLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

//Configuration Variables
var width = window.innerWidth - 18;
var base = 50;
var trackHeight = 6;

// var providedHeight = (numTracks+1) * trackHeight; //numTracks is provided from backend
providedHeight = 100
// Section Heights
var refHeight = 38;
var featHeight = 10;
var varHeight = 10;
var readsHeight = providedHeight; //Default variable: this will change based on number of reads

// Svg Containers for Each Div
var refContainer = d3.select("#refArea")
  .append("svg")
    .attr("width", width)
    .attr("height", refHeight);

var featureSvgContainer = d3.select("#featArea")
  .append("svg")
    .attr("height", featHeight)
    .attr("width", width);

var varSvgContainer = d3.select("#varArea")
  .append("svg")
    .attr("width", width)
    .attr("height", varHeight);

var readsSvgContainer = d3.select("#readsArea")
  .append("svg")
    .attr("height", (providedHeight+base))
    .attr("width", width);

// Hover box for reads
var div = d3.select("#readsArea")
  .append("div")
  .attr("class", "tooltip")
  .style("opacity", 0);

// Vertical Guidance Lines
var refVertLine = refContainer.append('line')
  .attr({
    'x1': 0,
    'y1': 0,
    'x2': 0,
    'y2': refHeight
  })
  .attr("stroke", "#002900")
  .attr("class", "verticalLine");

var featVertLine = featureSvgContainer.append('line')
  .attr({
    'x1': 0,
    'y1': 0,
    'x2': 0,
    'y2': featHeight
  })
  .attr("stroke", "#002900")
  .attr("class", "verticalLine");

var varVertLine = varSvgContainer.append('line')
  .attr({
    'x1': 0,
    'y1': 0,
    'x2': 0,
    'y2': varHeight
  })
  .attr("stroke", "#002900")
  .attr("class", "verticalLine");

var readsVertLine = readsSvgContainer.append('line')
  .attr({
    'x1': 50,
    'y1': 0,
    'x2': 50,
    'y2': readsHeight
  })
  .attr("stroke", "#002900")
  .attr("class", "verticalLine");

// Mousemove for svg containers
refContainer.on('mousemove', function () {
    var xPosition = d3.mouse(this)[0];
    d3.selectAll(".verticalLine")
      .attr({
        "x1" : xPosition,
        "x2" : xPosition
      })
});

featureSvgContainer.on('mousemove', function () {
  var xPosition = d3.mouse(this)[0];
  d3.selectAll(".verticalLine")
    .attr({
      "x1" : xPosition,
      "x2" : xPosition
    })
});

varSvgContainer.on('mousemove', function () {
    var xPosition = d3.mouse(this)[0];
    d3.selectAll(".verticalLine")
      .attr({
        "x1" : xPosition,
        "x2" : xPosition
      })
});

readsSvgContainer.on('mousemove', function () {
  var xPosition = d3.mouse(this)[0];
  d3.selectAll(".verticalLine")
    .attr({
      "x1" : xPosition,
      "x2" : xPosition
    })
});

//All rendering of data, and everything setting new region parameters, is done here
render(viewRegStart, viewRegEnd)

// Functions

function render(start, end) {
  //Adding Reference rectangles
  viewRegStart = start
  viewRegEnd = end

  //Add Region Info
  d3.select("h2")
    .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);

  //removes everything on every render, but will be changed it future
  refContainer.selectAll("g").remove()
  featureSvgContainer.selectAll("g").remove()
  varSvgContainer.selectAll("g").remove()
  readsSvgContainer.selectAll("g").remove()

  readJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  referenceStringLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  //Reference
  renderReference()

  //Features
  if (featuresExist === true) {
    renderFeatures()
  } else {
    document.getElementById("featArea").innerHTML = "No Features File Loaded"
  }

  //Variants
  if (variantsExist === true) {
    renderVariants()
  } else {
    document.getElementById("varArea").innerHTML = "No Variants File Loaded"
  }

  //Reads
  if (readsExist === true) {
    renderReads()
  } else {
    document.getElementById("readsArea").innerHTML = "No Reads File Loaded"
  }
}

function renderReference() {
  // Create the scale for the axis
  var axisScale = d3.scale.linear()
      .domain([viewRegStart, viewRegEnd])
      .range([0, width]);

  // Create the axis
  var xAxis = d3.svg.axis()
     .scale(axisScale);

  // Add the axis to the container
  refContainer.append("g")
      .attr("class", "axis")
      .call(xAxis);

  d3.json(referenceStringLocation, function(error, data) {
    refContainer.selectAll("rect").data(data)
    .enter()
      .append("g")
      .append("rect")
        .attr("x", function(d, i) {
          return i/(viewRegEnd-viewRegStart) * width;
        })
        .attr("y", 30)
        .attr("fill", function(d) {
          if (d.reference === "G") {
            return '#00C000'; //GREEN
          } else if (d.reference === "C") {
            return '#E00000'; //CRIMSON
          } else if (d.reference === "A") {
            return '#5050FF'; //AZURE
          } else if (d.reference == "T") {
            return '#E6E600'; //TWEETY BIRD
          }
        })
        .attr("width", function(d) {
          return Math.max(1, width/(viewRegEnd-viewRegStart));
        })
        .attr("height", refHeight)
        .on("mouseover", function(d) {
          div.transition()
            .duration(200)
            .style("opacity", .9);
          div.html(d.reference)
            .style("left", (d3.event.pageX - 10) + "px")
            .style("top", (d3.event.pageY - 30) + "px");
        })
  });
}

function renderFeatures() {

  d3.json(featureJsonLocation, function(error, data) {
      // Add the rectangles
    featureSvgContainer.selectAll("rect").data(data)
      .enter()
        .append("g")
        .append("rect")
          .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
          .attr("y", 0)
          .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
          .attr("height", featHeight)
          .attr("fill", "#6600CC")
          .on("mouseover", function(d) {
            div.transition()
            .duration(200)
            .style("opacity", .9);
            div .html(d.featureId)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
          })
          .on("mouseout", function(d) {
            div.transition()
            .duration(500)
            .style("opacity", 0);
          });
    });
}

function renderVariants() {

  d3.json(varJsonLocation, function(error, data) {

    // Add the rectangles
    varSvgContainer.selectAll("rect").data(data)
      .enter()
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
          .on("mouseover", function(d) {
            div.transition()
            .duration(200)
            .style("opacity", .9);
            div .html(d.alleles)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
          })
          .on("mouseout", function(d) {
            div.transition()
            .duration(500)
            .style("opacity", 0);
          });
  });
}

function renderReads() {

  // Create the scale for the axis
  var axisScale = d3.scale.linear()
      .domain([viewRegStart, viewRegEnd])
      .range([0, width]);

  // Create the axis
  var xAxis = d3.svg.axis()
     .scale(axisScale);

  d3.json(readJsonLocation,function(error, data) {

    numTracks = d3.max(data, function(d) {return d.track}) //+coerces all values into int
    readsHeight = (numTracks+1)*trackHeight

    // Reset size of svg container
    readsSvgContainer.attr("height", (readsHeight+ base))

    // Add the rectangles
    readsSvgContainer.selectAll("rect").data(data)
      .enter()
        .append("g")
        .append("rect")
          .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
          .attr("y", (function(d) { return readsHeight - trackHeight * (d.track+1); }))
          .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
          .attr("height", (trackHeight-2))
          .attr("fill", "steelblue")
          .on("mouseover", function(d) {
            div.transition()
              .duration(200)
              .style("opacity", .9);
            div .html(d.readName)
              .style("left", (d3.event.pageX) + "px")
              .style("top", (d3.event.pageY - 28) + "px");
          })
          .on("mouseout", function(d) {
            div.transition()
            .duration(500)
            .style("opacity", 0);
          });
      
    // Add the axis to the container
    readsSvgContainer.append("g")
      .attr("class", "axis")
      .attr("transform", "translate(0, " + readsHeight + ")")
      .call(xAxis);

    // Update height of vertical guide line
    readsVertLine.attr("y2", readsHeight)

  });


}

// Try to move very far left
function moveVeryFarLeft() {
  var newStart = Math.max(0, viewRegStart - (viewRegEnd-viewRegStart));
  var newEnd = Math.max(newStart, viewRegEnd - (viewRegEnd-viewRegStart));
  render(newStart, newEnd);
}

// Try to move far left
function moveFarLeft() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
  var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/2));
  render(newStart, newEnd);
}

// Try to move left
function moveLeft() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/4));
  var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4));
  render(newStart, newEnd);
}

 // Try to move right
 function moveRight() {
   var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
   var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/4);
   render(newStart, newEnd);
}

// Try to move far right
function moveFarRight() {
  var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/2);
  var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
  render(newStart, newEnd);
}

// Try to move very far right
function moveVeryFarRight() {
  var newStart = viewRegStart + (viewRegEnd-viewRegStart);
  var newEnd = viewRegEnd + (viewRegEnd-viewRegStart);
  render(newStart, newEnd);
}

// Try to zoom in
function zoomIn() {
  var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
  var newEnd = viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4);
  render(newStart, newEnd);
}

// Try to zoom out
function zoomOut() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
  var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
  render(newStart, newEnd);
}

// Redirect based on form input
function checkForm(form) {
  var newStart = Math.max(0, form.start.value);
  var newEnd = Math.max(newStart, form.end.value);
  form.reset();
  render(newStart, newEnd);
}
