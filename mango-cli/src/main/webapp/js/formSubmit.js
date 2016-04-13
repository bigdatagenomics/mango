/*****
* Handles form submission that triggers alignment and variant
* rendering
***/

// Autocomplete function for input submit forms
function autoComplete(dictionary) {
    $('#autocomplete').autocomplete({
      lookup: dictionary,
      onSelect: function (suggestion) {
        var thehtml = '<strong>Name:</strong> '+suggestion.value;
        $('#outputcontent').html(thehtml);
      }
    });
}


// on enter submits search form for chromosomal position
$('.variant-search').keypress(function (e) {
  if (e.which == 13) {
    checkVariantForm(this.form);
    return false;
  }
});

// Get and validate form info, reference regions
function validateFormElements(form) {
  var info = form.info.value;

  // validate that input has correct form
  try {
      var refName = info.split(":")[0];
      var region = info.split(":")[1].split("-");
      var newStart = Math.max(0, region[0]);
      var newEnd = Math.max(newStart, region[1]);
  }
  catch(err) {
      form[0].style.borderColor = "red";
      return undefined;
  }

  // Check that the each form input is correct
  if ( info===""|| refName==""|| isNaN(newStart) || isNaN(newEnd) || newStart>=newEnd){
    form[0].style.borderColor = "red";
    return undefined;
  }
  else{
    form[0].style.borderColor = "";
    setGlobalReferenceRegion(refName, newStart, newEnd);

    return {
            refName: refName,
            newStart: newStart,
            newEnd: newEnd
        };
  }
}

// Redirect based on form input
function checkForm(form) {
  var elements = validateFormElements(form);
  var quality = form.elements["quality"].value;
  if (elements != undefined) {
    render(elements.refName, elements.newStart, elements.newEnd, quality);
  }
}

function checkVariantForm(form) {
  var elements = validateFormElements(form);
  if (elements != undefined) {
    renderVariantFrequency();
    renderVariants(elements.refName, elements.newStart, elements.newEnd);
    renderReference(elements.refName, elements.newStart, elements.newEnd);
  }
}