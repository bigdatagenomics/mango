//Configuration Variables
var base = 50;
var trackHeight = 14;

// Section Heights
var refHeight = 38;
var featHeight = 10;
var varHeight = 10;
var readsHeight = 0; //Default variable: this will change based on number of reads

// Global Data
var sampleData;

// Functions
function render(refName, start, end) {

  //Updating Search Bar 
  document.getElementById("autocomplete").value = refName+":"+start.toString()+"-"+end.toString();
  
  //Adding Reference rectangles
  setGlobalReferenceRegion(refName, start, end);

  //Add Region Info
  var placeholder = viewRefName + ":"+ viewRegStart + "-" + viewRegEnd;
  $('#regInput').attr('placeholder', placeholder);

  // Reference
  renderReference(refName, start, end, function(valid){
        toggleContent(valid);
  });

  // Features
  if (featuresExist) {
    renderFeatures(refName, start, end);
  } else {
    $("#featArea").css( ' display', 'none' );
  }

  // Variants
  if (variantsExist) {
    // hide reads and disable checkbox
    if (end - start > 1000) {
      $(".viewVariants").attr("checked", false);
      $(".variantData").hide();

      $(".viewVariants").prop("disabled", true)
    } else $(".viewVariants").prop("disabled", false);
    renderVariantSummary(refName, start, end);  }

  // Reads and Coverage
  if (readsExist) {
    // hide reads and disable checkbox
    if (end - start > 5000) {
      $(".viewAlignments").attr("checked", false);
      $(".alignmentData").hide();

      $(".viewAlignments").prop("disabled", true)
    } else $(".viewAlignments").prop("disabled", false);
    renderMergedReads(refName, start, end);
  }
}

function toggleContent(validContent) {
  if (validContent) {
    $("#home").css("display", "none");
    $("#tracks").css("display", "block");
  } else {
    $("#home").css("display", "block");
    $("#tracks").css("display", "none");
  }


}