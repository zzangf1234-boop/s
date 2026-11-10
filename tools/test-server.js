require('http').createServer(function (q, s) {
  require('fs').readFile(require('path').join(__dirname, 'display.html'), function (e, d) {
    if (e) { s.statusCode = 500; s.end(String(e)); return; }
    s.setHeader('Content-Type', 'text/html; charset=utf-8');
    s.end(d);
  });
}).listen(8899, function () { console.log('ok 8899'); });
