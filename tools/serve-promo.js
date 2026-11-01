/* tools/promo.html 과 imgs/ 를 8642 포트로 서빙하는 최소 정적 서버 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const root = 'C:/Users/rheld/Desktop/\uD074\uB85C\uB4DC\uCF54\uB4DC/repo';
const types = {'.html':'text/html; charset=utf-8','.png':'image/png','.jpg':'image/jpeg','.css':'text/css','.js':'text/javascript'};
http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]);
  if (p === '/' ) p = '/tools/promo.html';
  const f = path.join(root, p);
  fs.readFile(f, (err, data) => {
    if (err) { res.writeHead(404); res.end('not found'); return; }
    res.writeHead(200, {'Content-Type': types[path.extname(f).toLowerCase()] || 'application/octet-stream'});
    res.end(data);
  });
}).listen(8642, () => console.log('promo server on http://localhost:8642'));
