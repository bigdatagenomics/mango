jQuery(function($) {

  // allows divs to be vertically resized at bottom
  // $('.resize-vertical').resizable({
  //   handles: 's',
  //   minHeight: 200,
  //   ghost: true
  // });

  $(".resize-vertical")
    .wrap('<div/>')
      .css({'overflow':'hidden'})
        .parent()
          .css({'display':'inline-block',
                'overflow':'hidden',
                'height': '100%',
                'width': '100%'

              }).resizable({
                handles: 's',
                minHeight: 200,
                ghost: true
              })
                  .find('.resize-vertical')
                    .css({overflow:'auto',
                          width:'100%',
                          height:'100%'});

});
