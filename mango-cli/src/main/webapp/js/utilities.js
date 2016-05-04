function getAlignmentSelector(sample) {
    var selector = "#" + sample + ">.alignmentData";
    return selector;
}

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

// Toggles alignment record contained in the selector for a given sample
function toggleAlignments(sample, selector) {
    var selector = $(getAlignmentSelector(filterName(sample)));
    if (!selector.is(':visible')) {
            renderAlignments(viewRefName, viewRegStart, viewRegEnd, mapQuality, sample);
        }
        $(selector).slideToggle( "fast" );
}

function setGlobalMapQ(mapq) {
    mapQuality = mapq;
}

// Filters invalid characters from string to create javascript descriptor
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

