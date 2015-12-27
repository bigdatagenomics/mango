// Filters invalid characters from string to create javascript descriptor
function filterName(str,i) {
  var nStr = str.replace("/","");
  nStr = nStr + i;
  i++;
  return nStr;
}

function checkboxChange() {
  for (var i = 0; i < samples.length; i++) {
    if (indelCheck.checked) {
      renderMismatches(readsData[i], samples[i]);
    } else  {
      readsSvgContainer[samples[i]].selectAll(".mismatch").remove();
    }
    if (coverageCheck.checked) {
      $(".sampleCoverage").show();
    } else {
      $(".sampleCoverage").hide();
    }
  }
}

function renderReference(viewRefName, viewRegStart, viewRegEnd) {
  var refHeight = 38;
  var jsonLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  // Svg Containers for refArea (exists is all views)
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
      .range([0, width]);

  // Create the axis
  var refAxis = d3.svg.axis()
     .scale(refAxisScale);

  // Add the axis to the container
  refContainer.append("g")
      .attr("class", "axis")
      .call(refAxis);

  d3.json(jsonLocation, function(error, data) {
      refSequence = data;
      // render reference for low or high resolution depending on base range
      if (viewRegEnd - viewRegStart > 100) {
        renderLowResRef(data, refContainer, refDiv);
      } else {
        renderHighResRef(data, refContainer);
      }
  });


}

// Renders reference at colored base resolution
function renderLowResRef(data, refContainer, refDiv) {

  var rects = refContainer.selectAll("rect").data(data)

  rects.enter()
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
        } else if (d.reference === "T") {
          return '#FFCC00'; //TWEETY BIRD
        } else if (d.reference === "N") {
          return '#FFFFFF'; //WHITE
        }
      })
      .attr("width", function(d) {
        return Math.max(1, width/(viewRegEnd-viewRegStart));
      })
      .attr("height", refHeight);
      // .on("click", function(d) {
      //   refDiv.transition()
      //     .duration(200)
      //     .style("opacity", .9);
      //   refDiv.html(
      //     "Base: " + d.reference + "<br>" +
      //     "Position: " + d.position)
      //     .style("left", (d3.event.pageX) + "px")
      //     .style("top", (d3.event.pageY - 28) + "px");
      // })
      // .on("mouseover", function(d) {
      //   refDiv.transition()
      //     .duration(200)
      //     .style("opacity", .9);
      //   refDiv.html(d.reference)
      //     .style("left", (d3.event.pageX - 10) + "px")
      //     .style("top", (d3.event.pageY - 30) + "px");
      // })
      // .on("mouseout", function(d) {
      //     refDiv.transition()
      //     .duration(500)
      //     .style("opacity", 0);
      //   });

    var removed = rects.exit();
    removed.remove();
}

// Renders reference at per base resolution
function renderHighResRef(data, refContainer) {
  var letters = refContainer.selectAll("hrtext")
                  .data(data)

  var refString = letters.enter()
                  .append("text");

  refString.attr("y", 30)
      .attr("x", 0)
      .attr("dx", function(d, i) {
             return i/(viewRegEnd-viewRegStart) * width - 5;
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
          return '#FFFFFF'; //WHITE
        }
      });

    var removed = letters.exit();
    removed.remove();
}

// Try to move very far left
function moveVeryFarLeft() {
  var newStart = Math.max(0, viewRegStart - (viewRegEnd-viewRegStart));
  var newEnd = Math.max(newStart, viewRegEnd - (viewRegEnd-viewRegStart));
  render(viewRefName, newStart, newEnd);
}

// Try to move far left
function moveFarLeft() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
  var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/2));
  render(viewRefName, newStart, newEnd);
}

// Try to move left
function moveLeft() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/4));
  var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4));
  render(viewRefName, newStart, newEnd);
}

 // Try to move right
 function moveRight() {
   var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
   var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/4);
   render(viewRefName, newStart, newEnd);
}

// Try to move far right
function moveFarRight() {
  var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/2);
  var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
  render(viewRefName, newStart, newEnd);
}

// Try to move very far right
function moveVeryFarRight() {
  var newStart = viewRegStart + (viewRegEnd-viewRegStart);
  var newEnd = viewRegEnd + (viewRegEnd-viewRegStart);
  render(viewRefName, newStart, newEnd);
}

// Try to zoom in
function zoomIn() {
  var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
  var newEnd = viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4);
  render(viewRefName, newStart, newEnd);
}

// Try to zoom out
function zoomOut() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
  var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
  render(viewRefName, newStart, newEnd);
}
var re = /(?:\.([^.]+))?$/;

// Upload new file
$("#loadFile:file").change(function(){
  var filename = $("#loadFile:file").val();
  var ext = re.exec(filename)[1];

  if (ext == "bam" || ext == "vcf" || ext == "adam") {
    samples.push(filename);
  }

});

// Upload new reference file
$("#loadRef:file").change(function(){
  var filename = $("#loadRef:file").val();
});

// Redirect based on form input
function checkForm(form) {
  var info = form.info.value;
  sampleId = info.split(":")[0]
  var refName = info.split(":")[1];
  var region = info.split(":")[2].split("-");
  var newStart = Math.max(0, region[0]);
  var newEnd = Math.max(newStart, region[1]);
  render(refName, newStart, newEnd);
}
