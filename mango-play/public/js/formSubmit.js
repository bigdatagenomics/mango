/*****
* Handles form submission that triggers alignment and variant
* rendering
***/

// Validates user input for form
$('.controls>button').click(function(){
    validateFormElements();
});

function validateFormElements() {

  // Validate input form syntax
  var form = $("#form-field");
  var region = form.val().split("-");

  try {
      var newStart = Math.max(0, region[0]);
      var newEnd = Math.max(newStart, region[1]);
  }
  catch(err) {
      form[0].style.borderColor = "red";
  }

  // Check that the each form input is correct
  if ( region===""|| isNaN(newStart) || isNaN(newEnd) || newStart>=newEnd){
    form[0].style.borderColor = "red";
  }
  else{
    form[0].style.borderColor = "";
  }
}
/* Validates whether form query has defined start, end and name elements
 * @return true if valid elements, false if invalid elements
 */
function validRegion() {
  if (typeof viewRegStart == "undefined" || typeof viewRegEnd == "undefined" || typeof viewRefName == "undefined")
    return false
  else return true
}
