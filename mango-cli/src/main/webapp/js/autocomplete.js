$(function(){
  // Read in the raw data from the sequence dictionary 

  // var choices = [
  //   // { value: ' ', data: ' ' },
  //   { value: 'chrM:1-100'},
  //   { value: 'chrM_a:1-100'},
  //   { value: 'chrM_b:1-100'},
  //   { value: 'chrM_c:1-100'},
  //   { value: 'chrM_d:1-100'},
  // ];
  // document.write(dictionary +"\n");
  // dictionary; //
  var choices = ['chrM:1-100', 'chrM_a:1-100', 'chrM_b:1-100', 'chrM_c:1-100', 'chrM_d:1-100'];
  // setup autocomplete function pulling from choices[] array
  $('#autocomplete').autocomplete({
    lookup: choices,
    onSelect: function (suggestion) {
      var thehtml = '<strong>Name:</strong> '+suggestion.value;
      $('#outputcontent').html(thehtml);
    }
  });
});