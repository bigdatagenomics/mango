// Filters invalid characters from string to create javascript descriptor
// Svg Containers for refArea (exists is all views)
var refHeight = 38;
var refWidth = $(".col-md-10.graphArea").width();

var refContainer = d3.select("#refArea")
  .append("svg")
  .attr("width", refWidth)
  .attr("height", refHeight);

function renderReference(viewRefName, viewRegStart, viewRegEnd) {
  var jsonLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

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

  // Making hover box
  var refDiv = d3.select("#refArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  refContainer.select(".axis").remove();

  // Updating Axis
  // Create the scale for the axis
  var refAxisScale = d3.scale.linear()
      .domain([viewRegStart, viewRegEnd])
      .range([0, refWidth]);

  // Create the axis
  var refAxis = d3.svg.axis()
     .scale(refAxisScale);

  // Add the axis to the container
  refContainer.append("g")
      .attr("class", "axis")
      .call(refAxis);

  d3.json(jsonLocation, function(error, data) {

    toggleReferenceDependencies(data);

    refSequence = data;

    // render reference for low or high resolution depending on base range
    if (viewRegEnd - viewRegStart > 100) {
      renderLowResRef(data, refContainer, refDiv);
    } else {
      renderHighResRef(data, refContainer);
    }
  });


}

/**
* Toggles DOM elements based on whether reference is provided
*/
function toggleReferenceDependencies(data) {
  if (data.length == 0) {
    $(".refDependancy").addClass("refDisabled");
  } else {
    $(".refDependancy").removeClass("refDisabled");
  }
}


// Renders reference at colored base resolution
function renderLowResRef(data, refContainer, refDiv) {

  refContainer.selectAll(".reftext").remove();

  var rects = refContainer.selectAll(".refrect").data(data);

  var modify = rects.transition();
  modify
    .attr("x", function(d, i) {
      return i/(viewRegEnd-viewRegStart) * refWidth;
    })
    .attr("width", function(d) {
      return Math.max(1, refWidth/(viewRegEnd-viewRegStart));
    })
    .attr("fill", function(d) {
      if (d.reference === "G") {
        return '#00C000'; //GREEN
      } else if (d.reference === "C") {
        return '#E00000'; //CRIMSON
      } else if (d.reference === "A") {
        return '#5050FF'; //AZURE
      } else if (d.reference === "T") {
        return '#E6E600'; //TWEETY BIRD
      } else if (d.reference === "N") {
        return '#000000'; //BLACK
      }
    });

    var newData = rects.enter();
    newData
    .append("g")
    .append("rect")
      .attr("class", "refrect")
      .attr("x", function(d, i) {
        return i/(viewRegEnd-viewRegStart) * refWidth;
      })
      .attr("y", 30)
      .attr("fill", function(d) {
        if (d.reference === "G") {
          return '#00C000'; //GREEN
        } else if (d.reference === "C") {
          return '#E00000'; //CRIMSON
        } else if (d.reference === "A") {
          return '#5050FF'; //AZURE
        } else if (d.reference === "T") {
          return '#FFCC00'; //TWEETY BIRD
        } else if (d.reference === "N") {
          return '#000000'; //BLACK
        }
      })
      .attr("width", function(d) {
        return Math.max(1, refWidth/(viewRegEnd-viewRegStart));
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
          .style("top", (d3.event.pageY - 28) + "px");
      })
      .on("mouseover", function(d) {
        refDiv.transition()
          .duration(200)
          .style("opacity", .9);
        refDiv.html(d.reference)
          .style("left", (d3.event.pageX - 10) + "px")
          .style("top", (d3.event.pageY - 30) + "px");
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
             return i/(viewRegEnd-viewRegStart) * refWidth - 5;
          })
      .text( function (d) { return d.reference; })
      .attr("fill", function(d) {
        if (d.reference === "G") {
          return '#00C000'; //GREEN
        } else if (d.reference === "C") {
          return '#E00000'; //CRIMSON
        } else if (d.reference === "A") {
          return '#5050FF'; //AZURE
        } else if (d.reference === "T") {
          return '#FFCC00'; //TWEETY BIRD
        } else if (d.reference === "N") {
          return '#000000'; //BLACK
        }
      });

  var newData = refString.enter();
  newData
      .append("text")
      .attr("class", "reftext")
      .attr("y", 30)
      .attr("x", 0)
      .attr("dx", function(d, i) {
             return i/(viewRegEnd-viewRegStart) * refWidth - 5;
          })
      .text( function (d) { return d.reference; })
      .attr("font-family", "Sans-serif")
      .attr("font-weight", "bold")
      .attr("font-size", "12px")
      .attr("fill", function(d) {
        if (d.reference === "G") {
          return '#00C000'; //GREEN
        } else if (d.reference === "C") {
          return '#E00000'; //CRIMSON
        } else if (d.reference === "A") {
          return '#5050FF'; //AZURE
        } else if (d.reference === "T") {
          return '#FFCC00'; //TWEETY BIRD
        } else if (d.reference === "N") {
          return '#000000'; //BLACK
        }
      });

    var removed = refString.exit();
    removed.remove();
}
