package com.store.didcall;

/** 휴대폰 브라우저에서 http://TV주소:8765 로 접속하면 보이는 호출 페이지 (tools/web.html 에서 생성) */
final class WebPage {
    private WebPage() {}

    static final String HTML =
        "<!doctype html>\n" +
        "<html lang=\"ko\"><head><meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">\n" +
        "<title>DID 번호호출</title>\n" +
        "<style>\n" +
        "*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}\n" +
        "body{margin:0;font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;background:#111418;color:#f2f2f2;display:flex;flex-direction:column;min-height:100vh;padding:14px;gap:10px;max-width:520px;margin:0 auto}\n" +
        ".now{font-size:14px;color:#9aa3ad;display:flex;justify-content:space-between}\n" +
        ".now b{color:#ffcc33;font-size:18px}\n" +
        "#disp{background:#000;border-radius:14px;text-align:center;font-size:64px;font-weight:800;padding:10px;min-height:96px;letter-spacing:4px;color:#ffcc33}\n" +
        ".pad{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;flex:1}\n" +
        "button{border:0;border-radius:14px;font-size:28px;font-weight:700;padding:16px 0;background:#262b31;color:#fff}\n" +
        "button:active{filter:brightness(1.4)}\n" +
        ".call{background:#ff8a00;font-size:30px;padding:20px 0}\n" +
        ".row{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}\n" +
        ".row button{font-size:17px;background:#33404d}\n" +
        "#msg{text-align:center;font-size:14px;color:#9aa3ad;min-height:18px}\n" +
        "details{font-size:14px;color:#9aa3ad}\n" +
        "details input{width:100%;padding:10px;border-radius:8px;border:0;margin:4px 0 8px;font-size:15px}\n" +
        "details button{font-size:16px;padding:10px;width:100%;background:#33404d}\n" +
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
        "<details><summary>TV 안내 설정</summary>\n" +
        "음성 문구 ({n} 자리에 번호)<input id=\"tts\">\n" +
        "화면 안내 문구<input id=\"sub\">\n" +
        "반복 횟수 (0~5)<input id=\"rep\" type=\"number\" min=\"0\" max=\"5\">\n" +
        "<button onclick=\"saveCfg()\">저장</button>\n" +
        "</details>\n" +
        "<script>\n" +
        "var v='',last=0,loaded=false;\n" +
        "function show(){document.getElementById('disp').textContent=v}\n" +
        "function k(d){if(v.length<6){v+=d;show()}}\n" +
        "function del(){v=v.slice(0,-1);show()}\n" +
        "function msg(t){document.getElementById('msg').textContent=t}\n" +
        "function render(s){\n" +
        " document.getElementById('cur').textContent=s.current||'-';\n" +
        " document.getElementById('hist').textContent=(s.history||[]).slice(0,4).join(' · ');\n" +
        " if(!loaded){document.getElementById('tts').value=s.tts;document.getElementById('sub').value=s.sub;document.getElementById('rep').value=s.repeat;loaded=true}\n" +
        "}\n" +
        "function api(p){return fetch(p).then(function(r){return r.json()}).then(function(s){render(s);if(!s.ok)msg(s.error||'오류');return s}).catch(function(){msg('TV에 연결할 수 없습니다')})}\n" +
        "function callNum(){if(!v){msg('번호를 입력하세요');return}var n=v;api('/call?n='+encodeURIComponent(n)).then(function(s){if(s&&s.ok){msg(n+'번 호출 완료');last=parseInt(n,10)||last;v='';show()}})}\n" +
        "function next(){v=String((last||parseInt(document.getElementById('cur').textContent,10)||0)+1);show()}\n" +
        "function saveCfg(){api('/config?tts='+encodeURIComponent(document.getElementById('tts').value)+'&sub='+encodeURIComponent(document.getElementById('sub').value)+'&repeat='+document.getElementById('rep').value).then(function(){msg('설정 저장됨')})}\n" +
        "api('/status');setInterval(function(){fetch('/status').then(function(r){return r.json()}).then(render).catch(function(){})},3000);\n" +
        "</script></body></html>\n";
}
