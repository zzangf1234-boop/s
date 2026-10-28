package com.store.didcall;

/** 휴대폰 브라우저 호출·관리 페이지 (/) — tools/web.html 에서 자동 생성 (직접 수정하지 마세요) */
final class WebPage {
    private WebPage() {}

    static final String HTML =
        "<!doctype html>\n" +
        "<html lang=\"ko\"><head><meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">\n" +
        "<title>DID 번호호출</title>\n" +
        "<style>\n" +
        "*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}\n" +
        "body{margin:0 auto;font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;background:#111418;color:#f2f2f2;display:flex;flex-direction:column;min-height:100vh;padding:14px;gap:10px;max-width:520px}\n" +
        ".now{font-size:14px;color:#9aa3ad;display:flex;justify-content:space-between}\n" +
        ".now b{color:#ffcc33;font-size:18px}\n" +
        "#disp{background:#000;border-radius:14px;text-align:center;font-size:64px;font-weight:800;padding:10px;min-height:96px;letter-spacing:4px;color:#ffcc33}\n" +
        ".pad{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}\n" +
        "button{border:0;border-radius:14px;font-size:28px;font-weight:700;padding:16px 0;background:#262b31;color:#fff;font-family:inherit}\n" +
        "button:active{filter:brightness(1.4)}\n" +
        ".call{background:#ff8a00;font-size:30px;padding:20px 0}\n" +
        ".row{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}\n" +
        ".row button{font-size:17px;background:#33404d}\n" +
        "#msg{text-align:center;font-size:14px;color:#9aa3ad;min-height:18px}\n" +
        "details{font-size:14px;color:#9aa3ad;background:#1c2127;border-radius:14px;padding:12px}\n" +
        "summary{font-size:16px;font-weight:700;color:#f2f2f2;cursor:pointer}\n" +
        "details input,details select{width:100%;padding:10px;border-radius:8px;border:0;margin:4px 0 10px;font-size:15px;font-family:inherit}\n" +
        "details button{font-size:16px;padding:12px;width:100%;background:#33404d;margin-top:4px}\n" +
        ".thumbs{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin:10px 0}\n" +
        ".thumb{position:relative;aspect-ratio:16/9;background:#000;border-radius:8px;overflow:hidden}\n" +
        ".thumb img{width:100%;height:100%;object-fit:cover;display:block}\n" +
        ".thumb button{position:absolute;top:3px;right:3px;width:28px;height:28px;padding:0;font-size:16px;border-radius:50%;background:rgba(0,0,0,.7);margin:0}\n" +
        ".up{display:block;text-align:center;background:#ff8a00;color:#fff;font-weight:700;font-size:16px;padding:12px;border-radius:14px;cursor:pointer}\n" +
        ".empty{grid-column:1/-1;text-align:center;padding:14px 0}\n" +
        "</style></head><body>\n" +
        "<div class=\"now\"><span>현재 호출 <b id=\"cur\">-</b></span><span id=\"hist\"></span></div>\n" +
        "<div id=\"disp\"></div>\n" +
        "<div class=\"pad\">\n" +
        "<button onclick=\"k('1')\">1</button><button onclick=\"k('2')\">2</button><button onclick=\"k('3')\">3</button>\n" +
        "<button onclick=\"k('4')\">4</button><button onclick=\"k('5')\">5</button><button onclick=\"k('6')\">6</button>\n" +
        "<button onclick=\"k('7')\">7</button><button onclick=\"k('8')\">8</button><button onclick=\"k('9')\">9</button>\n" +
        "<button onclick=\"del()\">←</button><button onclick=\"k('0')\">0</button><button onclick=\"v='';show()\">C</button>\n" +
        "</div>\n" +
        "<button class=\"call\" onclick=\"callNum()\">호출</button>\n" +
        "<div class=\"row\"><button onclick=\"api('/recall')\">재호출</button><button onclick=\"next()\">다음 번호</button><button onclick=\"api('/clear')\">화면 비우기</button></div>\n" +
        "<div id=\"msg\"></div>\n" +
        "<details id=\"imgbox\"><summary>TV 이미지 (왼쪽 화면)</summary>\n" +
        "<div class=\"thumbs\" id=\"thumbs\"></div>\n" +
        "<label class=\"up\">＋ 이미지 추가<input type=\"file\" accept=\"image/*\" multiple hidden onchange=\"upload(this.files);this.value=''\"></label>\n" +
        "<br>슬라이드 넘김 간격 (초)<input id=\"slide\" type=\"number\" min=\"3\" max=\"300\">\n" +
        "표시 방식<select id=\"fit\"><option value=\"contain\">이미지 전체 보이기</option><option value=\"cover\">화면 꽉 채우기 (가장자리 잘림)</option></select>\n" +
        "<button onclick=\"saveCfg()\">저장</button>\n" +
        "<button onclick=\"if(confirm('TV 이미지를 모두 삭제할까요?'))api('/image/clear').then(loadThumbs)\">이미지 모두 삭제</button>\n" +
        "</details>\n" +
        "<details><summary>호출 안내 설정</summary>\n" +
        "<br>음성 문구 ({n} 자리에 번호)<input id=\"tts\">\n" +
        "팝업 안내 문구<input id=\"sub\">\n" +
        "음성 반복 횟수 (0~5, 0이면 딩동만)<input id=\"rep\" type=\"number\" min=\"0\" max=\"5\">\n" +
        "팝업 표시 시간 (초)<input id=\"popup\" type=\"number\" min=\"3\" max=\"60\">\n" +
        "최근 호출 표시 시간 (분, 0이면 계속 표시)<input id=\"keep\" type=\"number\" min=\"0\" max=\"120\">\n" +
        "<button onclick=\"saveCfg()\">저장</button>\n" +
        "</details>\n" +
        "<details><summary>PC 브라우저를 호출 화면으로 쓰기</summary>\n" +
        "<p style=\"margin:10px 0;line-height:1.6\">TV에 연결된 윈도우 PC의 크롬·엣지에서 아래 주소를 열면 TV와 같은 호출 화면이 나옵니다.<br><b id=\"dispurl\" style=\"color:#ffcc33\"></b></p>\n" +
        "<button onclick=\"location.href='/display'\">이 브라우저에서 호출 화면 열기</button>\n" +
        "<button onclick=\"location.href='/display.bat'\">윈도우 실행 파일 받기 (DID-Display.bat)</button>\n" +
        "<p style=\"margin-top:8px;line-height:1.6\">실행 파일을 더블클릭하면 엣지 전체화면 + 소리 자동 허용으로 열립니다. 윈도우 시작 시 자동 실행하려면 Win+R → <b>shell:startup</b> 폴더에 넣으세요.</p>\n" +
        "</details>\n" +
        "<script>\n" +
        "var v='',last=0,loaded=false;\n" +
        "function $(i){return document.getElementById(i)}\n" +
        "function show(){$('disp').textContent=v}\n" +
        "function k(d){if(v.length<6){v+=d;show()}}\n" +
        "function del(){v=v.slice(0,-1);show()}\n" +
        "function msg(t){$('msg').textContent=t}\n" +
        "function render(s){\n" +
        " $('cur').textContent=s.current||'-';\n" +
        " $('hist').textContent=(s.history||[]).slice(0,4).join(' · ');\n" +
        " if(!loaded){$('tts').value=s.tts;$('sub').value=s.sub;$('rep').value=s.repeat;$('slide').value=s.slide;$('popup').value=s.popup;$('fit').value=s.fit;$('keep').value=s.keep;loaded=true}\n" +
        "}\n" +
        "function api(p){return fetch(p).then(function(r){return r.json()}).then(function(s){render(s);if(!s.ok)msg(s.error||'오류');return s}).catch(function(){msg('호출기에 연결할 수 없습니다')})}\n" +
        "function callNum(){if(!v){msg('번호를 입력하세요');return}var n=v;api('/call?n='+encodeURIComponent(n)).then(function(s){if(s&&s.ok){msg(n+'번 호출 완료');last=parseInt(n,10)||last;v='';show()}})}\n" +
        "function next(){v=String((last||parseInt($('cur').textContent,10)||0)+1);show()}\n" +
        "function saveCfg(){var q=['tts','sub'].map(function(i){return i+'='+encodeURIComponent($(i).value)});q.push('repeat='+$('rep').value,'slide='+$('slide').value,'popup='+$('popup').value,'keep='+$('keep').value,'fit='+$('fit').value);api('/config?'+q.join('&')).then(function(s){if(s&&s.ok)msg('설정 저장됨')})}\n" +
        "function loadThumbs(){fetch('/images').then(function(r){return r.json()}).then(function(d){var t=$('thumbs');t.innerHTML='';if(!d.images.length){t.innerHTML='<div class=\"empty\">등록된 이미지가 없습니다</div>';return}\n" +
        " d.images.forEach(function(n){var e=document.createElement('div');e.className='thumb';e.innerHTML='<img loading=\"lazy\" src=\"/img/'+n+'\"><button title=\"삭제\">✕</button>';e.querySelector('button').onclick=function(){if(confirm('이 이미지를 삭제할까요?'))api('/image/delete?name='+n).then(loadThumbs)};t.appendChild(e)})}).catch(function(){})}\n" +
        "function resize(file){return new Promise(function(res,rej){var img=new Image();img.onload=function(){var m=1920,w=img.naturalWidth,h=img.naturalHeight,s=Math.min(1,m/Math.max(w,h));var c=document.createElement('canvas');c.width=Math.round(w*s);c.height=Math.round(h*s);var x=c.getContext('2d');x.fillStyle='#fff';x.fillRect(0,0,c.width,c.height);x.drawImage(img,0,0,c.width,c.height);URL.revokeObjectURL(img.src);c.toBlob(function(b){b?res(b):rej()},'image/jpeg',0.88)};img.onerror=function(){rej()};img.src=URL.createObjectURL(file)})}\n" +
        "async function upload(files){var ok=0,err='';for(var i=0;i<files.length;i++){msg('이미지 보내는 중… '+(i+1)+'/'+files.length);try{var b=await resize(files[i]);var r=await fetch('/image',{method:'POST',headers:{'Content-Type':'image/jpeg'},body:b});var s=await r.json();if(s.ok)ok++;else err=s.error}catch(e){err='보내기 실패'}}msg(err?('이미지 '+ok+'장 추가 · 실패: '+err):('✔ 이미지 '+ok+'장 추가됨'));loadThumbs()}\n" +
        "$('imgbox').addEventListener('toggle',function(){if(this.open)loadThumbs()});\n" +
        "// 텐키(숫자 키패드): 숫자 입력, Enter 호출, . 한 칸 지우기, - 전체 삭제, + 다음 번호, * 재호출, / 화면 비우기\n" +
        "var NUMPAD={Numpad0:'0',Numpad1:'1',Numpad2:'2',Numpad3:'3',Numpad4:'4',Numpad5:'5',Numpad6:'6',Numpad7:'7',Numpad8:'8',Numpad9:'9',NumpadEnter:'Enter',NumpadAdd:'+',NumpadSubtract:'-',NumpadMultiply:'*',NumpadDivide:'/',NumpadDecimal:'.',NumpadComma:'.'};\n" +
        "document.addEventListener('keydown',function(e){var t=e.target.tagName;if(t==='INPUT'||t==='SELECT'||t==='TEXTAREA'||e.ctrlKey||e.altKey||e.metaKey)return;\n" +
        " var k=NUMPAD[e.code]||e.key,h=true; // 텐키는 NumLock 상태와 상관없이 키 위치(code)로 판단\n" +
        " if(/^[0-9]$/.test(k)){if(!e.repeat)window.k(k)}else if(k==='Enter'){if(!e.repeat)callNum()}else if(k==='.'||k===','||k==='Delete'||k==='Backspace'){del()}else if(k==='-'||k==='Escape'){v='';show()}\n" +
        " else if(k==='+'){if(!e.repeat)next()}else if(k==='*'){if(!e.repeat)api('/recall').then(function(s){if(s&&s.ok)msg('재호출했습니다')})}else if(k==='/'){if(!e.repeat)api('/clear').then(function(s){if(s&&s.ok)msg('화면을 비웠습니다')})}else h=false;\n" +
        " if(h){e.preventDefault();if(document.activeElement&&document.activeElement.blur)document.activeElement.blur()}});\n" +
        "$('dispurl').textContent='http://'+location.host+'/display';\n" +
        "api('/status');setInterval(function(){fetch('/status').then(function(r){return r.json()}).then(render).catch(function(){})},3000);\n" +
        "</script></body></html>\n";
}
