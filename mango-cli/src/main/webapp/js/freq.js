// Global Variables
var maxFreq = 0;
var covWidth = $(".sampleCoverage").width();
var height = 100;

var svgContainer = {};
for (var i = 0; i < samples.length; i++) {
  var selector = "#" + samples[i] + ">.sampleCoverage";
  svgContainer[samples[i]] = d3.select(selector)
    .append("svg")
      .attr("class", "coverage-svg")
      .attr("height", (height))
      .attr("width", width);
}

// Function (accessor function) to return the position for the data that falls just left of the cursor
var bisectData = d3.bisector(function(d) {
  return d.base;
}).left;

function renderJsonCoverage(data, i) {
  data = typeof data != "undefined" ? data : [];
  maxFreq = d3.max(data, function(d) {return d.freq});
  maxFreq = typeof maxFreq != "undefined" ? maxFreq : 0;

  data = data.sort(function(a, b){ return d3.ascending(a.base, b.base); })

  // add first and last elements which may be removed from sampling
  if (data[0].base > viewRegStart) {
    data.unshift({base: 0, freq: data[0].freq});
  }
  if (data[data.length -1].base < viewRegEnd) {
     data.push({base: viewRegEnd, freq: data[data.length - 1].freq})
  }

  // Create the scale for the x axis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

  // Create the scale for the y axis
  var yAxisScale = d3.scale.linear()
    .domain([maxFreq, 0])
    .range([0, height]);

  // Create the scale for the data
  var dataScale = d3.scale.linear()
    .domain([0, maxFreq])
    .range([0, height]);

  // Specify the area for the data being displayed
  var freqArea = d3.svg.area()
    .x(function(d){return xAxisScale(d.base);})
    .y0(height)
    .y1(function(d){return dataScale(maxFreq-d.freq);})
    .interpolate("basis");

  var removed = svgContainer[samples[i]].selectAll("path").remove()

  // Add the data area shape to the graph
  svgContainer[samples[i]]
    .append("path")
    .attr("d", freqArea(data))
    .style("fill", "#B8B8B8");
  svgContainer[samples[i]].append("rect")
    .attr("width", width)
    .attr("x", 0)
    .attr("height", height)
    .style("fill", "none")
    .style("pointer-events", "all")
    .on("mouseover", function() { focus.style("display", null); })
    .on("mouseout", function() { focus.style("display", "none"); })
    .on("mousemove", mousemove);

  // What we use to add tooltips
  var focus = svgContainer[samples[i]].append("g")
    .style("display", "none");

  // Append the y guide line
  focus.append("line")
    .attr("class", "yGuide")
    .style("stroke", "red")
    .style("stroke-dasharray", "3,3")
    .style("opacity", 0.5)
    .attr("x1", 0)
    .attr("x2", width);

  // Text above line to display reference position
  focus.append("text")
    .attr("class", "above")
    .style("stroke", "black")
    .style("font-size", "12")
    .style("stroke-width", "1px")
    .style("opacity", 0.8)
    .attr("dx", 8)
    .attr("dy", "-.3em");

  // Text below line to display coverage
  focus.append("text")
    .attr("class", "below")
    .style("stroke", "black")
    .style("font-size", "12")
    .style("stroke-width", "1px")
    .style("opacity", 0.8)
    .attr("dx", 8)
    .attr("dy", "1em");

renderd3Line(svgContainer[samples[i]], height);

  function mousemove() {
    // Initial calibrates initial mouse offset due to y axis position
    var x0 = xAxisScale.invert(d3.mouse(this)[0]);
    var i = bisectData(data, x0, 1);
    var opt1 = data[i - 1];
    var opt2 = data[i];

    if (data.length == 0) {
      return
    }

    // Finds the position that is closest to the mouse cursor
    var d = (x0 - opt1.base) > (opt2.base - x0) ? opt2 : opt1;

    focus.select("text.above")
      .attr("transform",
        "translate(" + xAxisScale(d.base) + "," +
        yAxisScale(d.freq) + ")")
      .text("Position: " + d.base);

    focus.select("text.below")
      .attr("transform",
        "translate(" + xAxisScale(d.base) + "," +
        yAxisScale(d.freq) + ")")
      .text("Freq: " + d.freq);

    focus.select(".yGuide")
      .attr("transform",
        "translate(" + (width - 50) * -1 + "," +
          yAxisScale(d.freq) + ")")
      .attr("x2", width + width);
  }

}
