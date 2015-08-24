// Global Variables
var maxFreq = 0;
var jsonLocation = "/freq/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var width = window.innerWidth - 50;
var base = 50;
var height = window.innerHeight;

//Add Region Info
d3.select("h2")
  .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);

var svgContainer = d3.select("body")
  .append("svg")
  .attr("height", (height+base))
  .attr("width", (width+base));

render(viewRefName, viewRegStart, viewRegEnd);

// Function (accessor function) to return the position for the data that falls just left of the cursor
var bisectData = d3.bisector(function(d) {
  return d.base;
}).left;

function render(refName, start, end) {
  viewRefName = refName;
  viewRegStart = start;
  viewRegEnd = end;

  var jsonLocation = "/freq/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  // Removes at first to update frequency graph
  svgContainer.selectAll("g").remove()

  //Add Region Info
  d3.select("h2")
    .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);

  d3.json(jsonLocation, function(error, data) {
    maxFreq = d3.max(data, function(d) {return d.freq});

    // Create the scale for the x axis
    var xAxisScale = d3.scale.linear()
      .domain([viewRegStart, viewRegEnd])
      .range([0, width]);

    // Create the scale for the y axis
    var yAxisScale = d3.scale.linear()
      .domain([maxFreq, 0])
      .range([0, height]);

    // Create the x axis
    var xAxis = d3.svg.axis()
       .scale(xAxisScale)
       .ticks(5);

    // Create the y axis
    var yAxis = d3.svg.axis()
       .scale(yAxisScale)
       .orient("left")
       .ticks(5);

    // Add the x axis to the container
    svgContainer.append("g")
      .attr("class", "axis")
      .attr("transform", "translate(" + base + ", " + height + ")")
      .call(xAxis);

    // Add the y axis to the container
    svgContainer.append("g")
      .attr("class", "axis")
      .attr("transform", "translate(" + base + ", 0)")
      .call(yAxis);

    // Create the scale for the data
    var dataScale = d3.scale.linear()
      .domain([0, maxFreq])
      .range([0, height]);

    // Specify the area for the data being displayed
    var freqArea = d3.svg.area()
      .x(function(d){return base + (d.base-viewRegStart)/(viewRegEnd-viewRegStart) * width;})
      .y0(height)
      .y1(function(d){return dataScale(maxFreq-d.freq);});

    // Add the data area shape to the graph
    svgContainer.append("g")
      .append("path")
      .attr("d", freqArea(data))
      .style("fill", "steelblue");

    svgContainer.append("rect")
      .attr("width", width)
      .attr("x", 50)
      .attr("height", height)
      .style("fill", "none")
      .style("pointer-events", "all")
      .on("mouseover", function() { focus.style("display", null); })
      .on("mouseout", function() { focus.style("display", "none"); })
      .on("mousemove", mousemove);

    // What we use to add tooltips
    var focus = svgContainer.append("g")
      .style("display", "none");

    // Append the x guide line
    focus.append("line")
      .attr("class", "xGuide")
      .style("stroke", "red")
      .style("stroke-dasharray", "3,3")
      .style("opacity", 0.5)
      .attr("y1", 0)
      .attr("y2", height);

    // Append the y guide line
    focus.append("line")
      .attr("class", "yGuide")
      .style("stroke", "red")
      .style("stroke-dasharray", "3,3")
      .style("opacity", 0.5)
      .attr("x1", width)
      .attr("x2", width);

    // Append the circle tooltip
    focus.append("circle")
      .attr("class", "focus")
      .style("stroke", "black")
      .attr("r", 5);

    // Text above line to display reference position
    focus.append("text")
      .attr("class", "above")
      .style("stroke", "black")
      .style("stroke-width", "1px")
      .style("opacity", 0.8)
      .attr("dx", 8)
      .attr("dy", "-.3em");

    // Text below line to display coverage
    focus.append("text")
      .attr("class", "below")
      .style("stroke", "black")
      .style("stroke-width", "1px")
      .style("opacity", 0.8)
      .attr("dx", 8)
      .attr("dy", "1em");


    function mousemove() {  
      // Initial calibrates initial mouse offset due to y axis position
      var initial = xAxisScale.invert(50);
      var x0 = xAxisScale.invert(d3.mouse(this)[0])-initial;
      var i = bisectData(data, x0, 1);
      var opt1 = data[i - 1];
      var opt2 = data[i];

      // Finds the position that is closest to the mouse cursor
      var d = (x0 - opt1.base) > (opt2.base - x0) ? opt2 : opt1;

      // Adjusts position and text based on data closest to mouse cursor
      focus.select("circle.focus")
        .attr("transform",
          "translate(" + xAxisScale(d.base+initial) + "," +
          yAxisScale(d.freq) + ")");

      focus.select("text.above")
        .attr("transform",
          "translate(" + xAxisScale(d.base+initial) + "," +
          yAxisScale(d.freq) + ")")
        .text("Position: " + d.base);

      focus.select("text.below")
        .attr("transform",
          "translate(" + xAxisScale(d.base+initial) + "," +
          yAxisScale(d.freq) + ")")
        .text("Freq: " + d.freq);

      focus.select(".xGuide")
        .attr("transform",
          "translate(" + xAxisScale(d.base+initial) + "," +
            yAxisScale(d.freq) + ")")
        .attr("y2", height - yAxisScale(d.freq));

      focus.select(".yGuide")
        .attr("transform",
          "translate(" + (width - 50) * -1 + "," +
            yAxisScale(d.freq) + ")")
        .attr("x2", width + width);
    }

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
  console.log(form.info.value);
  var info = form.info.value;
  var refName = info.split(":")[0];
  var region = info.split(":")[1].split("-");
  var newStart = Math.max(0, region[0]);
  var newEnd = Math.max(newStart, region[1]);
  console.log(newStart)
  console.log(newEnd)
  render(refName, newStart, newEnd);
}
