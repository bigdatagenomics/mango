// Filters invalid characters from string to create javascript descriptor

var barWidth = 21;

function setGlobalReferenceRegion(refName, start, end) {
    viewRefName = refName;
    viewRegStart = start;
    viewRegEnd = end;
}

function isValidHttpResponse(data) {
  if (data == undefined || jQuery.isEmptyObject(data) || data.length == 0) {
    return false;
  } else {
    return true;
  }
}

// colors for base pairs [A, T, G, C]
var aColor = '#5050FF'; //AZURE
var cColor = '#E00000'; //CRIMSON
var tColor = '#E6E600'; //TWEETY BIRD
var gColor = '#00C000'; //GREEN
var nColor = '#D3D3D3'; // GREY

//var color = d3.scale.ordinal()
//    .range([aColor, cColor, tColor, gColor]);
//color.domain(["A", "T", "C", "G"]);
var baseColors = {
  'A': aColor,
  'C': cColor,
  'T': tColor,
  'G': gColor
}

function setGlobalMapQ(mapq) {
    mapQuality = mapq;
}

function filterNames(arr) {
  var filteredArr = [];
  for (var i = 0; i < arr.length; i++) {
    filteredArr[i] = arr[i].replace("/","");
  }
  return filteredArr;
}

function filterName(name) {
  return name.replace("/","");
}

Array.prototype.contains = function(v) {
  for(var i = 0; i < this.length; i++) {
      if(this[i] === v) return true;
  }
  return false;
};

Array.prototype.unique = function() {
    var arr = [];
    for(var i = 0; i < this.length; i++) {
        if(!arr.contains(this[i])) {
            arr.push(this[i]);
        }
    }
    return arr;
};

// calulates reads track height based on viewing range
function getTrackHeight() {
  var range = viewRegEnd - viewRegStart;
  var baseHeight = 14;

  if (range <= 1000)  {
    return baseHeight;
  } else if (range > 1000 && range < 10000)  {
    return baseHeight - 7;
  } else {
    return 4;
  }
}

function checkboxChange() {
  if (indelCheck.checked) {
    $(".indel").show();

  } else {
    $(".indel").hide();
  }

  if (mismatchCheck.checked) {
    $(".mrect").show();
  } else {
    $(".mrect").hide();
  }

  if (coverageCheck.checked) {
    $(".sampleCoverage").show();
  } else {
    $(".sampleCoverage").hide();
  }
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

