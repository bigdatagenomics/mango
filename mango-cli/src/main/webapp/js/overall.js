var readJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var referenceStringLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
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

// Svg Containers, and vertical guidance lines and animations set here for all divs

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

if (readsExist === true) {
  var readsSvgContainer = d3.select("#readsArea")
    .append("svg")
      .attr("height", (readsHeight+base))
      .attr("width", width);

  var readsVertLine = readsSvgContainer.append('line')
    .attr({
      'x1': 50,
      'y1': 0,
      'x2': 50,
      'y2': readsHeight
    })
    .attr("stroke", "#002900")
    .attr("class", "verticalLine");

  readsSvgContainer.on('mousemove', function () {
    var xPosition = d3.mouse(this)[0];
    d3.selectAll(".verticalLine")
      .attr({
        "x1" : xPosition,
        "x2" : xPosition
      })
  });

}


//All rendering of data, and everything setting new region parameters, is done here
render(viewRegStart, viewRegEnd);

// Functions
function render(start, end) {
  //Adding Reference rectangles
  viewRegStart = start;
  viewRegEnd = end;

  //Add Region Info
  d3.select("h2")
    .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);

  readJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  referenceStringLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  renderReference();

  // Features
  if (featuresExist === true) {
    renderFeatures();
  } 

  //Variants
  if (variantsExist === true) {
    renderVariants();
  }

  //Reads
  if (readsExist === true) {
    renderReads();
  }

}

function renderReference() {
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

  d3.json(referenceStringLocation, function(error, data) {

    refSequence = data
    var rects = refContainer.selectAll("rect").data(data);

    var modify = rects.transition();
    modify
      .attr("x", function(d, i) {
        return i/(viewRegEnd-viewRegStart) * width;
      })
      .attr("width", function(d) {
        return Math.max(1, width/(viewRegEnd-viewRegStart));
      });

    var newData = rects.enter();
    newData
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
            return '#E6E600'; //TWEETY BIRD
          } else if (d.reference === "N") {
            return '#FFFFFF'; //WHITE
          }
        })
        .attr("width", function(d) {
          return Math.max(1, width/(viewRegEnd-viewRegStart));
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
  });
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

function renderReads() {

  // Making hover box
  var readDiv = d3.select("#readsArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  // Create the scale for the axis
  var readsAxisScale = d3.scale.linear()
      .domain([viewRegStart, viewRegEnd])
      .range([0, width]);

  // Create the axis
  var readsAxis = d3.svg.axis()
     .scale(readsAxisScale);

  // Remove current axis to update it
  readsSvgContainer.select(".axis").remove();

  d3.json(readJsonLocation,function(error, data) {

    var numTracks = d3.max(data, function(d) {return d.track});
    readsHeight = (numTracks+1)*trackHeight;

    // Reset size of svg container
    readsSvgContainer.attr("height", (readsHeight+ base));

    // Add the axis to the container
    readsSvgContainer.append("g")
      .attr("class", "axis")
      .attr("transform", "translate(0, " + readsHeight + ")")
      .call(readsAxis);

    // Update height of vertical guide line
    readsVertLine.attr("y2", readsHeight);

    // Add the rectangles
    var rects = readsSvgContainer.selectAll(".readrect").data(data);

    var modify = rects.transition();
    modify
      .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("y", (function(d) { return readsHeight - trackHeight * (d.track+1); }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));
    
    var newData = rects.enter();
    newData
      .append("g")
      .append("rect")
        .attr("class", "readrect")
        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
        .attr("y", (function(d) { return readsHeight - trackHeight * (d.track+1); }))
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
        .attr("height", (trackHeight-2))
        .attr("fill", "steelblue")
        .on("click", function(d) {
          readDiv.transition()
            .duration(200)
            .style("opacity", .9);
          readDiv.html(
            "Read Name: " + d.readName + "<br>" +
            "Start: " + d.start + "<br>" +
            "End: " + d.end + "<br>" +
            "Cigar:" + d.cigar + "<br>" +
            "Track: " + d.track + "<br>" +
            "Reverse Strand: " + d.readNegativeStrand)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
        })
        .on("mouseover", function(d) {
          readDiv.transition()
            .duration(200)
            .style("opacity", .9);
          readDiv.html(d.readName)
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - 28) + "px");
        })
        .on("mouseout", function(d) {
          readDiv.transition()
          .duration(500)
          .style("opacity", 0);
        });
    
    var removed = rects.exit();
    removed.remove();

    renderMismatches(data);

    var arrowHeads = readsSvgContainer.selectAll("path").data(data);
    var arrowModify = arrowHeads.transition();
    arrowModify
      .attr("transform", function(d) { 
        if (d.readNegativeStrand === true) { // to the right
          var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width;
          var rectWidth = Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart)));
          var xCoord = rectStart + rectWidth;
          var yCoord = readsHeight - trackHeight * (d.track+1) +1;
          return "translate(" + xCoord + "," + yCoord + ") rotate(-30)"; 
        } else if (d.readNegativeStrand === false) { // to the left
          var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width;
          var xCoord = rectStart;
          var yCoord = readsHeight - trackHeight * (d.track+1) +1;
          return "translate(" + xCoord + "," + yCoord + ") rotate(30)"; 
        }
      })
      .style("fill", function(d) {
        if (d.readNegativeStrand === true) {
          return "red";
        } else if (d.readNegativeStrand === false) {
          return "green";
        }
      });

    var newArrows = arrowHeads.enter();
    newArrows
      .append("g")
      .append("path")
        .attr("d", d3.svg.symbol().type("triangle-up").size(22))
        .attr("transform", function(d) { 
          if (d.readNegativeStrand === true) { // to the right
            var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width;
            var rectWidth = Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart)));
            var xCoord = rectStart + rectWidth;
            var yCoord = readsHeight - trackHeight * (d.track+1) +1;
            return "translate(" + xCoord + "," + yCoord + ") rotate(-30)"; 
          } else if (d.readNegativeStrand === false) { // to the left
            var rectStart = (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width;
            var xCoord = rectStart;
            var yCoord = readsHeight - trackHeight * (d.track+1) +1;
            return "translate(" + xCoord + "," + yCoord + ") rotate(30)"; 
          }
        })
        .style("fill", function(d) {
          if (d.readNegativeStrand === true) {
            return "red";
          } else if (d.readNegativeStrand === false) {
            return "green";
          }
        });
    
    var removedArrows = arrowHeads.exit();
    removedArrows.remove();
    

  });
}


function renderMismatches(data) {
  var misMatchDiv = d3.select("#readsArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  var misMatchArr = [];
  var matchCompare = []

  data.forEach(function(d) {
    var curr = 0;
    var refCurr = d.start;
    var str = d.cigar.match(/(\d+|[^\d]+)/g);
    for (var i = 0; i < 2*str.length; i+=2) {
      var misLen = parseInt(str[i]);
      var op = str[i+1];
      if (op === "I") {
        //Operation, mismatch start on reference, mismatch start on read sequence, length, track 
        var misElem = [op, refCurr, curr, misLen, d.sequence.substring(curr, curr+misLen), d.track]; 
        misMatchArr.push(misElem);
      } else if (op === "D") {
        var misElem = [op, refCurr, curr, misLen, d.sequence.substring(curr, curr+misLen), d.track];
        misMatchArr.push(misElem);
      } else if (op === "X") { //mismatch
        var misElem = [op, refCurr, curr, misLen, d.sequence.substring(curr, curr+misLen), d.track];
        refCurr += misLen;
        misMatchArr.push(misElem);
      } else if (op === "M") {
        var misElem = [op, refCurr, curr, misLen, d.sequence.substring(curr, curr+misLen), d.track];
        refCurr += misLen;
        matchCompare.push(misElem);
      } else if (op === "N") {
        var misElem = [op, refCurr, curr, misLen, d.sequence.substring(curr, curr+misLen), d.track];
        misMatchArr.push(misElem);
      }
      curr += misLen;
    }

  });
  renderMCigar(matchCompare)

  //Operation, mismatch start on reference, mismatch start on read sequence, length, substring, track 
  var misRects = readsSvgContainer.selectAll(".mismatch").data(misMatchArr);
  var modMisRects = misRects.transition()
    .attr("x", (function(d) { 
      return (d[1]-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
    .attr("y", (function(d) { return readsHeight - (trackHeight * (d[5]+1)); }))
    .attr("width", (function(d) { 
      if (d[0] === "I") {
        return 5;
      } else if (d[0] === "D") {
        return 5;
      } else if (d[0] === "N") {
        return 5;
      }
      return Math.max(1,(d[3])*(width/(viewRegEnd-viewRegStart))); 
    }));

  var newMisRects = misRects.enter();
  newMisRects
    .append("g")
    .append("rect")
      .attr("class", "mismatch")
      .attr("x", (function(d) { 
        return (d[1]-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("y", (function(d) { return readsHeight - (trackHeight * (d[5]+1)); }))
      .attr("width", (function(d) { 
        if (d[0] === "I") {
          return 5;
        } else if (d[0] === "D") {
          return 5;
        } else if (d[0] === "N") {
          return 5;
        }
        return Math.max(1,(d[3])*(width/(viewRegEnd-viewRegStart))); }))
      .attr("height", (trackHeight-2))
      .attr("fill", function(d) {
        if (d[0] === "I") {
          return "pink";
        } else if (d[0] === "D") {
          return "black";
        } else if (d[0] === "N") {
          return "gray";
        }
      })
      .on("click", function(d) {
        misMatchDiv.transition()
          .duration(200)
          .style("opacity", .9);
        misMatchDiv.html(
          "Operation: " + d[0] + "<br>" +
          "Ref Start: " + d[1] + "<br>" +
          "Read Start: " + d[2] + "<br>" +
          "Length:" + d[3] + "<br>" +
          "Sequence: " + d[4] + "<br>" + 
          "Track: " + d[5])
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      })
      .on("mouseover", function(d) {
        misMatchDiv.transition()
          .duration(200)
          .style("opacity", .9);
        misMatchDiv.html(d)
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      })
      .on("mouseout", function(d) {
        misMatchDiv.transition()
        .duration(500)
        .style("opacity", 0);
      });

  var removedMisRects = misRects.exit();
  removedMisRects.remove();

}

function renderMCigar(data) {
  // Making hover box
  var misMatchDiv = d3.select("#readsArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  var rectArr = []
  data.forEach(function(d) {
    var readCount = 0;
    var sequence = d[4]
    readCount+=1
    posCount = 0;

    var limit = Math.min(d[3], viewRegEnd-d[1])
    for (var j = 0; j < limit; j++) {
      var currPos = d[1] + j;
      var currBase = sequence[j];
      var refIndex = currPos - viewRegStart;
      var refElem = refSequence[refIndex];
      posCount +=1
      var refBase = refElem.reference;
      if (String(currBase) === String(refBase)) {
      } else {
        var x = refIndex/(viewRegEnd-viewRegStart)* width;
        var y = readsHeight - trackHeight * (d[5]+1);
        var rectElem = [x, y, currBase, refBase];
        rectArr.push(rectElem);
      }
    }
  });
  
  var mRects = readsSvgContainer.selectAll(".mrect").data(rectArr);
  var modifiedMRects = mRects.transition()
  modifiedMRects
    .attr("x", function(d) { return d[0]})
    .attr("y", function(d) { return d[1]})
    .attr("fill", function(d) {
      currBase = d[2];
      if (currBase === "G") {
        return '#00C000'; //GREEN
      } else if (currBase === "C") {
        return '#E00000'; //CRIMSON
      } else if (currBase === "A") {
        return '#5050FF'; //AZURE
      } else if (currBase === "T") {
        return '#E6E600'; //TWEETY BIRD
      } else if (currBase === "N") {
        return 'black'; //WHITE
      }
    })
    .attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)));

  var newMRects = mRects.enter();
  newMRects
    .append("g")
    .append("rect")
    .attr("class", "mrect")
    .attr("x", function(d) { return d[0]})
    .attr("y", function(d) { return d[1]})
    .attr("width", Math.max(1, width/(viewRegEnd-viewRegStart)))
    .attr("height", (trackHeight-2))
    .attr("fill", function(d) {
      currBase = d[2];
      if (currBase === "G") {
        return '#00C000'; //GREEN
      } else if (currBase === "C") {
        return '#E00000'; //CRIMSON
      } else if (currBase === "A") {
        return '#5050FF'; //AZURE
      } else if (currBase === "T") {
        return '#E6E600'; //TWEETY BIRD
      } else if (currBase === "N") {
        return 'black'; //WHITE
      }
    })
    .on("click", function(d) {
      misMatchDiv.transition()
        .duration(200)
        .style("opacity", .9);
      misMatchDiv.html(
        "Ref Base: " + d[3] + "<br>" +
        "Base: " + d[2])
        .style("left", (d3.event.pageX) + "px")
        .style("top", (d3.event.pageY - 28) + "px");
    })
    .on("mouseover", function(d) {
      misMatchDiv.transition()
        .duration(200)
        .style("opacity", .9);
      misMatchDiv.html(
        "Ref Base: " + d[3] + "<br>" +
        "Base: " + d[2])
        .style("left", (d3.event.pageX - 10) + "px")
        .style("top", (d3.event.pageY - 30) + "px");
    })
    .on("mouseout", function(d) {
        misMatchDiv.transition()
        .duration(500)
        .style("opacity", 0);
      });
    var removedMRects = mRects.exit();
    removedMRects.remove()

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
