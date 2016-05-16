// Filters invalid characters from string to create javascript descriptor
// Svg Containers for refArea (exists is all views)
var refHeight = 38;
var width = $(".graphArea").width();

var refContainer = d3.select("#refArea")
  .append("svg")
  .attr("width", width)
  .attr("height", refHeight);

// Making hover box
var refDiv = d3.select("#refArea")
.append("div")
.attr("class", "tooltip")
.style("opacity", 0);

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

function renderReference(viewRefName, viewRegStart, viewRegEnd, callback) {
  var jsonLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  refContainer.select(".axis").remove();

  // Create the scale for the axis
 //  var xAxisScale = xAxis(viewRegStart, viewRegEnd, width);
 var xAxisScale = d3.scale.linear()
        .domain([viewRegStart, viewRegEnd])
        .range([0, width]);

  // Create the axis
  var xAxis = d3.svg.axis()
     .scale(xAxisScale);

  // Add the axis to the container
  refContainer.append("g")
      .attr("class", "axis")
      .call(xAxis);

  d3.json(jsonLocation, function(error, data) {
    if (error != null) {
        if (error.status == 404) { // data not found
            callback(false);      // if data not found, redirect to home page
            return;
        } else if (error.status == 413) { // entity too large
            //return;
        }
    }

    data = typeof data != "undefined" ? data : [];

    // render reference for low or high resolution depending on base range
    if (viewRegEnd - viewRegStart > 100) {
      renderLowResRef(data, refContainer, refDiv);
    } else {
      renderHighResRef(data, refContainer);
    }
    callback(true);
  });


}

// Renders reference at colored base resolution
function renderLowResRef(data, refContainer, refDiv) {

 // Render x axis
 var xAxisScale = xRange(viewRegStart, viewRegEnd, width)

  refContainer.selectAll(".reftext").remove();

  var rects = refContainer.selectAll(".refrect").data(data);

  var modify = rects.transition();
  modify
    .attr("x", function(d) {
      return xAxisScale(d.position);
    })
    .attr("width", function(d) {
    if (d.position < 0) return width;
    else return Math.max(1, width/(viewRegEnd-viewRegStart));
    })
    .attr("fill", function(d) {
      if (d.reference === "N") return nColor;
      else if (d.position == -1) return brown;
      else return baseColors[d.reference];
    });

    var newData = rects.enter();
    newData
    .append("rect")
      .attr("class", "refrect")
      .attr("x", function(d) {
        return xAxisScale(d.position);
      })
      .attr("y", 30)
    .attr("fill", function(d) {
      if (d.reference === "N") return nColor;
      else if (d.position == -1) return brown;
      else return baseColors[d.reference];
    })
    .attr("width", function(d) {
      if (d.position < 0) return width;
      else return Math.max(1, width/(viewRegEnd-viewRegStart));
    })
    .attr("height", refHeight)
    .on("click", function(d) {
        refDiv.transition()
          .duration(200)
          .style("opacity", .9);
        refDiv.html(
          "Base: " + d.reference + "<br>" +
          "Position: " + d.position)
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 100) + "px");
      })
      .on("mouseover", function(d) {
        refDiv.transition()
          .duration(200)
          .style("opacity", .9);
        refDiv.html(d.reference)
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 100) + "px");
      })
      .on("mouseout", function(d) {
          refDiv.transition()
          .duration(500)
          .style("opacity", 0);
        });

    var removed = rects.exit();
    removed.remove();
}

// Renders reference at per base resolution
function renderHighResRef(data, refContainer) {
  refContainer.selectAll(".refrect").remove();
  var refString = refContainer.selectAll(".reftext")
                  .data(data);

  var modify = refString.transition();
  modify
      .attr("x", 0)
      .attr("dx", function(d, i) {
           return i/(viewRegEnd-viewRegStart) * width - (width/(viewRegEnd-viewRegStart))/2 ;
      })
      .text( function (d) { return d.reference; })
      .attr("fill", function(d) {
        if (d.reference === "N") return nColor;
        else return baseColors[d.reference];
      });

  var newData = refString.enter();
  newData
      .append("text")
      .attr("class", "reftext")
      .attr("y", 30)
      .attr("x", 0)
      .attr("dx", function(d, i) {
        return i/(viewRegEnd-viewRegStart) * width - (width/(viewRegEnd-viewRegStart))/2 ;
          })
      .text( function (d) { return d.reference; })
      .attr("font-family", "Sans-serif")
      .attr("font-weight", "bold")
      .attr("font-size", "12px")
      .attr("fill", function(d) {
        if (d.reference === "N") return nColor;
        else return baseColors[d.reference];
      });

    var removed = refString.exit();
    removed.remove();
}
