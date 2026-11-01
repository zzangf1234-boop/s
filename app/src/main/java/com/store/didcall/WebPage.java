package com.store.didcall;

/** 휴대폰 브라우저 호출·관리 페이지 (/) — tools/web.html 에서 자동 생성 (직접 수정하지 마세요) */
final class WebPage {
    private WebPage() {}

    private static final String P0 =
        "<!doctype html>\n" +
        "<html lang=\"ko\"><head><meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">\n" +
        "<title>DID 번호호출</title>\n" +
        "<style>\n" +
        "*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}\n" +
        "body{margin:0 auto;font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic','Noto Sans KR',sans-serif;background:#111418;color:#f2f2f2;display:flex;flex-direction:column;min-height:100vh;padding:14px;gap:10px;max-width:520px}\n" +
        ".now{font-size:14px;color:#9aa3ad;display:flex;justify-content:space-between}\n" +
        ".now b{color:#ffcc33;font-size:18px}\n" +
        "#disp{background:#000;border-radius:14px;text-align:center;font-size:64px;font-weight:800;padding:10px;min-height:96px;letter-spacing:4px;color:#ffcc33}\n" +
        ".pad{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}\n" +
        "button{border:0;border-radius:14px;font-size:28px;font-weight:700;padding:16px 0;background:#262b31;color:#fff;font-family:inherit;cursor:pointer}\n" +
        "button:active{filter:brightness(1.4)}\n" +
        "button:disabled{opacity:.4}\n" +
        ".call{background:#ff8a00;font-size:30px;padding:20px 0}\n" +
        ".row{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}\n" +
        ".row button{font-size:17px;background:#33404d}\n" +
        "#msg{text-align:center;font-size:14px;color:#9aa3ad;min-height:18px}\n" +
        "#authbar{display:none;background:#3a2a10;border:1px solid #ff8a00;color:#ffd9a0;border-radius:12px;padding:10px 12px;font-size:14px;line-height:1.5}\n" +
        "#authbar.show{display:block}\n" +
        "#authbar button{font-size:14px;padding:8px 12px;margin-top:6px;background:#ff8a00}\n" +
        "details{font-size:14px;color:#9aa3ad;background:#1c2127;border-radius:14px;padding:12px}\n" +
        "summary{font-size:16px;font-weight:700;color:#f2f2f2;cursor:pointer}\n" +
        "details input,details select{width:100%;padding:10px;border-radius:8px;border:0;margin:4px 0 10px;font-size:15px;font-family:inherit}\n" +
        "details input[type=checkbox]{width:auto;margin:0 8px 0 0;transform:scale(1.3)}\n" +
        "details input[type=range]{padding:0}\n" +
        "details label.ck{display:flex;align-items:center;color:#f2f2f2;margin:6px 0 12px}\n" +
        "details button{font-size:16px;padding:12px;width:100%;background:#33404d;margin-top:4px}\n" +
        "details button.sm{font-size:14px;padding:8px}\n" +
        ".two{display:grid;grid-template-columns:1fr 1fr;gap:8px}\n" +
        "#laycanvas{position:relative;background:#000;border-radius:10px;margin:10px auto;width:100%;touch-action:none;user-select:none}\n" +
        "#laycanvas.p{width:56%}\n" +
        ".lb{position:absolute;background:#33404d;border:2px solid #ffcc33;border-radius:8px;color:#fff;font-size:12px;display:flex;align-items:flex-start;justify-content:center;cursor:move;overflow:hidden;box-sizing:border-box}\n" +
        ".lb.off{opacity:.35;border-style:dashed;border-color:#9aa3ad;pointer-events:none}\n" +
        ".lb span{margin-top:3px;padding:0 3px;text-align:center;line-height:1.2;pointer-events:none}\n" +
        ".lb i{position:absolute;right:0;bottom:0;width:20px;height:20px;background:#ffcc33;border-radius:6px 0 6px 0;cursor:nwse-resize}\n" +
        "#laylist label{display:flex;align-items:center;gap:6px;font-size:14px;margin:10px 0;color:#f2f2f2}\n" +
        "#laylist label input{width:auto;margin:0;transform:scale(1.2)}\n" +
        "#laylist select{width:auto;margin:0 0 0 6px;padding:4px 6px}\n" +
        ".thumbs{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin:10px 0}\n" +
        ".thumb{position:relative;aspect-ratio:16/9;background:#000;border-radius:8px;overflow:hidden}\n" +
        ".thumb img,.thumb video{width:100%;height:100%;object-fit:cover;display:block}\n" +
        ".thumb .badge{position:absolute;left:4px;top:4px;background:rgba(0,0,0,.7);color:#fff;font-size:11px;border-radius:4px;padding:1px 4px}\n" +
        ".thumb .ctl{position:absolute;left:0;right:0;bottom:0;display:flex;justify-content:space-between;background:linear-gradient(transparent,rgba(0,0,0,.75))}\n" +
        ".thumb .ctl button{width:30px;height:28px;padding:0;font-size:14px;border-radius:6px;background:transparent;margin:0}\n" +
        ".up{display:block;text-align:center;background:#ff8a00;color:#fff;font-weight:700;font-size:16px;padding:12px;border-radius:14px;cursor:pointer}\n" +
        ".empty{grid-column:1/-1;text-align:center;padding:14px 0}\n" +
        ".note{line-height:1.6;margin:6px 0 10px}\n" +
        ".krow{display:grid;grid-template-columns:1fr auto;gap:4px 8px;align-items:center;padding:8px 0;border-top:1px solid #2a3038}\n" +
        ".kname{color:#f2f2f2;font-weight:700;font-size:15px}\n" +
        ".kchips{grid-column:1/2;display:flex;flex-wrap:wrap;gap:4px}\n" +
        ".chip{background:#262b31;color:#ffcc33;border-radius:6px;padding:3px 6px;font-size:13px}\n" +
        ".chip b{color:#9aa3ad;cursor:pointer;margin-left:2px}\n" +
        ".none{font-size:13px}\n" +
        "details .kset{grid-row:1/3;grid-column:2;width:auto;font-size:14px;padding:10px 12px;margin:0}\n" +
        "details .kset.on{background:#ff8a00}\n" +
        "#waitbox{display:none;font-size:13px;color:#9aa3ad}\n" +
        "#waitbox.show{display:block}\n" +
        "#waitlist{display:flex;gap:6px;overflow-x:auto;padding:4px 0 2px}\n" +
        "#waitlist button{flex:none;min-width:72px;font-size:22px;padding:10px 8px;background:#33404d;position:relative}\n" +
        "#waitlist button:first-child{background:#ff8a00}\n" +
        "#waitlist button i{position:absolute;top:-2px;right:3px;font-style:normal;font-size:13px;color:#ffd9a0}\n" +
        "#klog{white-space:pre-line;color:#f2f2f2;font-size:13px;line-height:1.6}\n" +
        "</style></head><body>\n" +
        "<div class=\"now\"><span>현재 호출 <b id=\"cur\">-</b></span><span id=\"hist\"></span></div>\n" +
        "<div id=\"waitbox\"><span id=\"waitlbl\">조리중</span><div id=\"waitlist\"></div></div>\n" +
        "<div id=\"authbar\"><span id=\"authtext\">🔐 이 기기는 아직 호출 권한이 없습니다. 호출기 폰에서 허용해야 번호 호출·설정을 할 수 있습니다.</span><br><button id=\"authbtn\" onclick=\"requestAuth()\">호출기 폰에 허용 요청 보내기</button></div>\n" +
        "<div id=\"disp\"></div>\n" +
        "<div class=\"pad\">\n" +
        "<button onclick=\"k('1')\">1</button><button onclick=\"k('2')\">2</button><button onclick=\"k('3')\">3</button>\n" +
        "<button onclick=\"k('4')\">4</button><button onclick=\"k('5')\">5</button><button onclick=\"k('6')\">6</button>\n" +
        "<button onclick=\"k('7')\">7</button><button onclick=\"k('8')\">8</button><button onclick=\"k('9')\">9</button>\n" +
        "<button onclick=\"del()\">←</button><button onclick=\"k('0')\">0</button><button onclick=\"v='';show()\">C</button>\n" +
        "</div>\n" +
        "<button class=\"call\" onclick=\"callNum()\">호출</button>\n" +
        "<div class=\"row\" style=\"grid-template-columns:repeat(4,1fr)\"><button onclick=\"tilde()\">~ 연속</button><button onclick=\"api('/recall').then(okMsg('재호출했습니다'))\">재호출</button><button onclick=\"next()\">다음 번호</button><button onclick=\"api('/clear').then(okMsg('화면을 비웠습니다'))\">화면 비우기</button></div>\n" +
        "<div id=\"msg\"></div>\n" +
        "<details id=\"imgbox\"><summary>TV 이미지·동영상 (왼쪽 화면)</summary>\n" +
        "<div class=\"thumbs\" id=\"thumbs\"></div>\n" +
        "<label class=\"up\">＋ 이미지·동영상 추가<input type=\"file\" accept=\"image/*,video/*\" multiple hidden onchange=\"upload(Array.prototype.slice.call(this.files));this.value=''\"></label>\n" +
        "<p class=\"note\">이미지는 자동으로 선명한 크기(최대 2560px)로 맞춰 보냅니다. 동영상은 mp4 · 1080p 이하 · 300MB 이하를 권장합니다. 썸네일의 ◀ ▶ 로 순서를 바꿉니다.</p>\n" +
        "슬라이드 넘김 간격 (초, 동영상은 끝까지 재생)<input id=\"slide\" type=\"number\" min=\"3\" max=\"300\">\n" +
        "표시 방식<select id=\"fit\"><option value=\"contain\">전체 보이기</option><option value=\"cover\">화면 꽉 채우기 (가장자리 잘림)</option></select>\n" +
        "화면 색감<select id=\"theme\"><option value=\"vivid\">선명 (검정 배경, 밝은 글자)</option><option value=\"calm\">차분 (회색 배경)</option></select>\n" +
        "<label class=\"ck\"><input type=\"checkbox\" id=\"videoSound\">동영상 소리 켜기 (호출 안내 중에는 작아짐)</label>\n" +
        "<button onclick=\"saveCfg()\">저장</button>\n" +
        "<button onclick=\"if(confirm('TV 이미지·동영상을 모두 삭제할까요?'))api('/media/clear').then(loadThumbs)\">모두 삭제</button>\n" +
        "</details>\n" +
        "<details><summary>호출 안내 설정</summary>\n" +
        "<br>음성 문구 ({n} 자리에 번호)<input id=\"tts\">\n" +
        "팝업 안내 문구<input id=\"sub\">\n" +
        "음성 반복 횟수 (0~5, 0이면 알림음만)<input id=\"rep\" type=\"number\" min=\"0\" max=\"5\">\n" +
        "팝업 표시 시간 (초)<input id=\"popup\" type=\"number\" min=\"3\" max=\"60\">\n" +
        "최근 호출 표시 시간 (분, 0이면 계속 표시)<input id=\"keep\" type=\"number\" min=\"0\" max=\"120\">\n" +
        "<button onclick=\"saveCfg()\">저장</button>\n" +
        "</details>\n" +
        "<details id=\"soundbox\"><summary>알림음 · 목소리</summary>\n" +
        "<br>알림음<select id=\"chime\"></select>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"new Audio('/chime?type='+$('chime').value).play()\">▶ 알림음 듣기</button>\n" +
        "<label class=\"up\" style=\"font-size:14px;padding:8px;margin-top:4px\">📁 내 소리 파일<input type=\"file\" accept=\"audio/*\" hidden onchange=\"uploadChime(this.files[0]);this.value=''\"></label></div>\n" +
        "<br>음성 방식<select id=\"voiceMode\" onchange=\"voiceModeUi()\"><option value=\"tts\">기계 음성</option><option value=\"rec\">내 목소리 (녹음은 호출기 앱 ⚙ → 내 목소리 녹음)</option></select>\n" +
        "<div id=\"packbox\" style=\"display:none\">번호 뒤 안내 문장<select id=\"packPhrase\"></select></div>\n" +
        "<div id=\"ttsbox\">음성 엔진<select id=\"ttsEngine\"></select>\n" +
        "목소리<select id=\"voiceName\"></select>\n" +
        "말하기 속도 <b id=\"rateV\"></b><input id=\"rate\" type=\"range\" min=\"50\" max=\"200\" step=\"5\" oninput=\"$('rateV').textContent=this.value+'%'\">\n" +
        "목소리 높낮이 <b id=\"pitchV\"></b><input id=\"pitch\" type=\"range\" min=\"50\" max=\"200\" step=\"5\" oninput=\"$('pitchV').textContent=this.value+'%'\"></div>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"previewVoice()\">▶ 51번 미리 듣기</button><button class=\"sm\" onclick=\"saveSound()\">저장</button></div>\n" +
        "</details>\n" +
        "<details id=\"closebox\"><summary>영업 시간 · 마감 안내</summary>\n" +
        "<label class=\"ck\" style=\"margin-top:12px\"><input type=\"checkbox\" id=\"closeOn\">영업 시간에 맞춰 자동으로 마감 안내</label>\n" +
        "<div class=\"two\"><div>영업 시작<input id=\"open\" type=\"time\"></div><div>영업 마감<input id=\"close\" type=\"time\"></div></div>\n" +
        "마감 멘트 (화면 표시 + 음성 안내)<input id=\"closeMsg\">\n" +
        "<p class=\"note\">마감 시각이 되면 알림음 + 마감 멘트를 한 번 안내하고 TV·PC 화면에 표시합니다. 다음 영업 시작 시각에 자동으로 사라집니다. 현재: <b id=\"closedNow\"></b></p>\n" +
        "<button onclick=\"saveClosing()\">저장</button>\n" +
        "<button id=\"closeNowBtn\" onclick=\"toggleClosed()\">지금 마감 표시</button>\n" +
        "</details>\n" +
        "<details id=\"layoutbox\"><summary>화면 배치 (TV·PC 화면 구역)</summary>\n" +
        "<p class=\"note\">구역을 끌어서 옮기고, 노란 모서리를 끌어서 크기를 바꿉니다. 가로 화면과 세로 화면은 따로 저장됩니다.</p>\n" +
        "<div class=\"two\"><button class=\"sm\" id=\"lay_l\" onclick=\"setLayTab('l')\">가로 화면</button><button class=\"sm\" id=\"lay_p\" onclick=\"setLayTab('p')\">세로 화면</button></div>\n" +
        "<div id=\"laycanvas\"></div>\n" +
        "<div id=\"laylist\"></div>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"layPreset('basic')\">기본형</button><button class=\"sm\" onclick=\"layPreset('flip')\">좌우 반전형</button></div>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"layPreset('big')\">번호 크게형</button><button class=\"sm\" onclick=\"layReset()\">처음 상태로</button></div>\n" +
        "<p class=\"note\">키오스크 연동을 쓰면 '조리중' 칸을 켜세요. 저장하면 TV·PC 화면에 바로 반영됩니다.</p>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"$('logoFile').click()\">로고 그림 올리기</button><button class=\"sm\" onclick=\"clearLogo()\">로고 지우기</button></div>\n" +
        "<input type=\"file\" id=\"logoFile\" accept=\"image/png,image/jpeg,image/webp,image/gif\" style=\"display:none\" onchange=\"uploadLogo(this.files[0]);this.value=''\">\n" +
        "<p class=\"note\" id=\"logoNote\"></p>\n" +
        "<button onclick=\"saveLayout()\">저장</button>\n" +
        "</details>\n" +
        "<details id=\"colorbox\"><summary>화면 색</summary>\n" +
        "<p class=\"note\">TV·PC 화면의 색을 바꿉니다. 미리 정해진 색 세트를 고른 뒤 세부 색을 바꿀 수 있습니다.</p>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"colPreset('vivid')\">선명</button><button class=\"sm\" onclick=\"colPreset('calm')\">차분</button></div>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"colPreset('cafe')\">카페</button><button class=\"sm\" onclick=\"colPreset('modern')\">모던</button></div>\n" +
        "<label class=\"ck\">배경 <input type=\"color\" id=\"col_bg\" style=\"width:44px;height:32px;margin:0 0 0 auto;padding:0\"></label>\n" +
        "<label class=\"ck\">칸 배경 <input type=\"color\" id=\"col_panel\" style=\"width:44px;height:32px;margin:0 0 0 auto;padding:0\"></label>\n" +
        "<label class=\"ck\">글자 (이름·라벨) <input type=\"color\" id=\"col_label\" style=\"width:44px;height:32px;margin:0 0 0 auto;padding:0\"></label>\n" +
        "<label class=\"ck\">강조 번호 (방금 부른 번호) <input type=\"color\" id=\"col_cur\" style=\"width:44px;height:32px;margin:0 0 0 auto;padding:0\"></label>\n" +
        "<label class=\"ck\">최근·조리중 번호 <input type=\"color\" id=\"col_num\" style=\"width:44px;height:32px;margin:0 0 0 auto;padding:0\"></label>\n" +
        "<label class=\"ck\">호출 팝업 배경 <input type=\"color\" id=\"col_card\" style=\"width:44px;height:32px;margin:0 0 0 auto;padding:0\"></label>\n" +
        "<div class=\"two\"><button class=\"sm\" onclick=\"saveColors()\">저장</button><button class=\"sm\" onclick=\"resetColors()\">기본 색으로</button></div>\n" +
        "</details>\n" +
        "<details id=\"keybox\"><summary>키 설정 (키패드·키보드)</summary>\n" +
        "<p class=\"note\">[키 지정]을 누른 뒤 키보드·키패드에서 원하는 키를 누르세요. 숫자 키는 항상 번호 입력입니다. 호출기 앱과 같은 설정을 사용합니다.</p>\n" +
        "<div id=\"keyrows\"></div>\n" +
        "<button onclick=\"presetKeys(KEY_TENKEY)\">텐키형 기본값 (. 한 칸 지우기)</button>\n";
    private static final String P1 =
        "<button onclick=\"presetKeys(KEY_BACKSPACE)\">백스페이스형 기본값 (← 한 칸 지우기)</button>\n" +
        "</details>\n" +
        "<details id=\"kioskbox\"><summary>🧾 키오스크 주문 연동 (조리중)</summary>\n" +
        "<label class=\"ck\" style=\"margin-top:12px\"><input type=\"checkbox\" id=\"kioskOn\">키오스크 주문서 받기 (조리중 목록)</label>\n" +
        "<p class=\"note\">분기 프로그램의 '네트워크 프린터' 주소: <b id=\"kaddr\" style=\"color:#ffcc33\"></b></p>\n" +
        "주방 프린터로 넘겨주기 (선택 · 예: 192.168.0.50:9100)<input id=\"relay\" placeholder=\"비워 두면 중계 안 함\">\n" +
        "주문서에서 번호 앞에 붙은 글자<input id=\"orderLabel\">\n" +
        "<button onclick=\"saveKiosk()\">저장</button>\n" +
        "<button onclick=\"if(confirm('조리중 목록을 모두 지울까요?'))api('/waiting/clear').then(okMsg('조리중을 비웠습니다'))\">조리중 비우기</button>\n" +
        "<p class=\"note\">최근 받은 주문서</p><div id=\"klog\">아직 없음</div>\n" +
        "</details>\n" +
        "<details><summary>PC 브라우저를 호출 화면으로 쓰기</summary>\n" +
        "<p class=\"note\">TV에 연결된 윈도우 PC의 크롬·엣지에서 아래 주소를 열면 TV와 같은 호출 화면이 나옵니다.<br><b id=\"dispurl\" style=\"color:#ffcc33\"></b></p>\n" +
        "<button onclick=\"location.href='/display'\">이 브라우저에서 호출 화면 열기</button>\n" +
        "<button onclick=\"location.href='/display.bat'\">윈도우 실행 파일 받기 (DID-Display.bat)</button>\n" +
        "<p class=\"note\">실행 파일을 더블클릭하면 엣지 전체화면 + 소리 자동 허용으로 열립니다. 윈도우 시작 시 자동 실행하려면 Win+R → <b>shell:startup</b> 폴더에 넣으세요. 화면 왼쪽 위를 3번 클릭하면 메뉴(새로고침·회전·최소화·종료)가 나옵니다. TV를 세로로 달았다면 메뉴의 [화면 회전]을 누르세요.</p>\n" +
        "</details>\n" +
        "<script>\n" +
        "var v='',last=0,loaded=false,S={};\n" +
        "function $(i){return document.getElementById(i)}\n" +
        "function show(){$('disp').textContent=v}\n" +
        "function k(d){var i=v.indexOf('~');if((i<0?v.length:v.length-i-1)<6){v+=d;show()}}\n" +
        "// 연속 호출: 번호 뒤에 ~ 한 번 (예: 1~4). 빈 칸이면 다음 번호부터\n" +
        "function tilde(){if(v&&v.indexOf('~')<0&&/^[0-9]+$/.test(v))v+='~';else if(!v){var n=(last||parseInt($('cur').textContent,10)||0)+1;v=n+'~'}show()}\n" +
        "function del(){v=v.slice(0,-1);show()}\n" +
        "function msg(t){$('msg').textContent=t}\n" +
        "function okMsg(t){return function(s){if(s&&s.ok)msg(t)}}\n" +
        "// ── 기기 승인: 호출·설정은 호출기 폰에서 [허용]한 기기만 ──\n" +
        "var TOK='';try{TOK=localStorage.getItem('didTok')||''}catch(e){}\n" +
        "function withTok(p){return p+(p.indexOf('?')<0?'?':'&')+'t='+encodeURIComponent(TOK)}\n" +
        "function devName(){var u=navigator.userAgent;return /iPhone|iPad/.test(u)?'iPhone·iPad 브라우저':/Android/.test(u)?'안드로이드 폰 브라우저':/Windows/.test(u)?'윈도우 PC 브라우저':/Mac/.test(u)?'Mac 브라우저':'브라우저'}\n" +
        "var asking=false;\n" +
        "function authBar(text,btn){$('authbar').classList.add('show');$('authtext').textContent=text;$('authbtn').style.display=btn?'':'none'}\n" +
        "function requestAuth(){if(asking)return;asking=true;authBar('🔐 호출기 폰에 허용 요청을 보냈습니다. 호출기 폰 화면(또는 알림)에서 [허용]을 눌러 주세요…',false);\n" +
        " fetch('/auth/request?name='+encodeURIComponent(devName())).then(function(r){return r.json()}).then(function(d){\n" +
        "  var tries=0;(function poll(){fetch('/auth/check?id='+d.id).then(function(r){return r.json()}).then(function(c){\n" +
        "   if(c.state==='ok'){TOK=c.token;try{localStorage.setItem('didTok',TOK)}catch(e){}asking=false;$('authbar').classList.remove('show');msg('✔ 허용되었습니다. 이제 호출할 수 있습니다');return}\n" +
        "   if(c.state==='pending'&&++tries<90){setTimeout(poll,2000);return}\n" +
        "   asking=false;authBar(c.state==='denied'?'호출기 폰에서 거부되었습니다.':'허용 요청 시간이 지났습니다.',true)}).catch(function(){asking=false;authBar('호출기에 연결할 수 없습니다.',true)})})()\n" +
        " }).catch(function(){asking=false;authBar('호출기에 연결할 수 없습니다.',true)})}\n" +
        "function render(s){\n" +
        " S=s;if(!capturing&&s.keys&&s.keys!==keysStr){keysStr=s.keys;KM=parseKeys(s.keys);renderKeys()}\n" +
        " $('cur').textContent=s.current||'-';\n" +
        " $('hist').textContent=(s.history||[]).slice(0,4).join(' · ');\n" +
        " renderWait(s);\n" +
        " if(!layBound&&s.layout!==undefined){layBound=true;layInit()}\n" +
        " if(!layLoaded&&s.layout!==undefined){LAY.l=layFix((s.layout&&s.layout.l)||layFrom('l','basic'));LAY.p=layFix((s.layout&&s.layout.p)||layFrom('p','basic'));layLoaded=true;layRender()}\n" +
        " if(!colLoaded&&s.colors!==undefined){colLoaded=true;colShow(s.colors||COL_PRESETS.vivid)}\n" +
        " if(s.authed===false&&!asking)authBar('🔐 이 기기는 아직 호출 권한이 없습니다. 호출기 폰에서 허용해야 번호 호출·설정을 할 수 있습니다.',true);\n" +
        " if(s.authed)$('authbar').classList.remove('show');\n" +
        " $('closedNow').textContent=s.closed?'🌙 마감 표시 중':'영업 중';$('closeNowBtn').textContent=s.closed?'마감 표시 끄기':'지금 마감 표시';\n" +
        " if(!loaded){$('tts').value=s.tts;$('sub').value=s.sub;$('rep').value=s.repeat;$('slide').value=s.slide;$('popup').value=s.popup;$('fit').value=s.fit;$('keep').value=s.keep;\n" +
        "  $('theme').value=s.theme||'vivid';$('videoSound').checked=!!s.videoSound;$('closeOn').checked=!!s.closeOn;$('open').value=s.open||'09:00';$('close').value=s.close||'22:00';$('closeMsg').value=s.closeMsg||'';\n" +
        "  $('rate').value=s.rate||95;$('rateV').textContent=(s.rate||95)+'%';$('pitch').value=s.pitch||100;$('pitchV').textContent=(s.pitch||100)+'%';$('voiceMode').value=s.voiceMode||'tts';\n" +
        "  $('kioskOn').checked=!!s.kioskOn;$('relay').value=s.relay||'';$('orderLabel').value=s.orderLabel||'주문번호';loaded=true}\n" +
        "}\n" +
        "function api(p){return fetch(withTok(p)).then(function(r){return r.json()}).then(function(s){if(s.auth){requestAuth();return s}if(s.current!==undefined)render(s);if(!s.ok)msg(s.error||'오류');return s}).catch(function(){msg('호출기에 연결할 수 없습니다')})}\n" +
        "// ── 조리중 (키오스크 주문) : 눌러서 호출 · 길게 눌러(또는 ✕) 삭제 ──\n" +
        "var waitSig='';\n" +
        "function renderWait(s){var w=s.waiting||[],on=!!s.kioskOn;$('waitbox').classList.toggle('show',on);\n" +
        " $('kaddr').textContent=location.hostname+' : '+(s.kioskPort||9100);\n" +
        " var lg=s.kioskLog||[],kl=$('klog');kl.innerHTML='';if(!lg.length)kl.textContent='아직 없음';lg.forEach(function(j){var line=(function(){var t=new Date(j.time),hh=('0'+t.getHours()).slice(-2)+':'+('0'+t.getMinutes()).slice(-2)+':'+('0'+t.getSeconds()).slice(-2);\n" +
        "  var r=!j.n?'번호 못 찾음':j.added?j.n+(j.reprint?'번 추가 (재발행)':'번 추가'):j.n+(j.reprint?'번 재발행 · 중복이라 무시':'번 중복이라 무시');if(j.items)r+=' · '+j.items;if(j.relay)r+=j.relay==='ok'?' · 주방 전달됨':' · ⚠ '+j.relay;return hh+'  '+r})();var d=document.createElement(j.id?'a':'div');d.textContent=line+(j.id?'  🔍':'');if(j.id){d.href=withTok('/kiosk/text?id='+j.id);d.target='_blank';d.style.cssText='display:block;color:#f2f2f2;text-decoration:none;padding:3px 0'}kl.appendChild(d)});\n" +
        " var sig=on+'|'+w.map(function(x){return x.n}).join(',');if(sig===waitSig)return;waitSig=sig;\n" +
        " $('waitlbl').textContent=w.length?'조리중 '+w.length+'건 · 눌러서 호출 · ✕ 삭제 · 빈 칸으로 [호출] = 맨 앞 번호':'조리중 · 키오스크 주문이 들어오면 여기에 표시됩니다';\n" +
        " var box=$('waitlist');box.innerHTML='';w.forEach(function(x){var b=document.createElement('button');b.textContent=x.n;b.title=x.items||'';\n" +
        "  var del=document.createElement('i');del.textContent='✕';b.appendChild(del);\n" +
        "  var held=false,tm=null;\n" +
        "  function rm(){if(confirm(x.n+'번'+(x.items?' ('+x.items+')':'')+'을 조리중에서 지울까요? (호출하지 않음)'))api('/waiting/remove?n='+x.n).then(okMsg(x.n+'번을 지웠습니다'))}\n" +
        "  b.addEventListener('touchstart',function(){held=false;tm=setTimeout(function(){held=true;rm()},650)},{passive:true});\n" +
        "  b.addEventListener('touchend',function(){clearTimeout(tm)});b.addEventListener('touchmove',function(){clearTimeout(tm)},{passive:true});\n" +
        "  b.oncontextmenu=function(e){e.preventDefault()};\n" +
        "  b.onclick=function(e){if(held){held=false;return}if(e.target===del){rm();return}callWait(x.n)};box.appendChild(b)})}\n" +
        "function callWait(n){api('/call?n='+encodeURIComponent(n)).then(function(s){if(s&&s.ok)msg(n+'번 호출 완료')})}\n" +
        "function saveKiosk(){api('/config?kioskOn='+($('kioskOn').checked?1:0)+'&'+q('relay')+'&'+q('orderLabel')).then(okMsg('키오스크 연동 설정 저장됨'))}\n" +
        "function callNum(){if(!v){if(S.kioskOn||(S.waiting&&S.waiting.length)){api('/call?next=1').then(function(s){if(s&&s.ok)msg(s.current+'번 호출 완료')});return}msg('번호를 입력하세요');return}var n=v.replace(/~$/,'');api('/call?n='+encodeURIComponent(n)).then(function(s){if(s&&s.ok){msg(n+'번 호출 완료');last=parseInt(n.split('~').pop(),10)||last;v='';show()}})}\n" +
        "function next(){v=String((last||parseInt($('cur').textContent,10)||0)+1);show()}\n" +
        "function q(id){return id+'='+encodeURIComponent($(id).value)}\n" +
        "function saveCfg(){var p=['tts','sub','slide','popup','keep','fit','theme'].map(q);p.push('repeat='+$('rep').value,'videoSound='+($('videoSound').checked?1:0));api('/config?'+p.join('&')).then(okMsg('설정 저장됨'))}\n" +
        "// ── 이미지·동영상 ──\n" +
        "function loadThumbs(){fetch('/media',{cache:'no-store'}).then(function(r){return r.json()}).then(function(d){var t=$('thumbs');t.innerHTML='';var items=d.items||[];if(!items.length){t.innerHTML='<div class=\"empty\">등록된 이미지·동영상이 없습니다</div>';return}\n" +
        " items.forEach(function(it){var n=it.name,e=document.createElement('div');e.className='thumb';\n" +
        "  e.innerHTML=(it.video?'<video muted preload=\"metadata\" src=\"/m/'+n+'#t=0.5\"></video><span class=\"badge\">▶ 동영상 '+Math.round(it.size/1048576)+'MB</span>':'<img loading=\"lazy\" src=\"/m/'+n+'\">')+'<div class=\"ctl\"><button data-m=\"-1\">◀</button><button data-d=\"1\">✕</button><button data-m=\"1\">▶</button></div>';\n" +
        "  e.querySelector('[data-d]').onclick=function(){if(confirm('삭제할까요?'))api('/media/delete?name='+n).then(loadThumbs)};\n" +
        "  e.querySelectorAll('[data-m]').forEach(function(b){b.onclick=function(){api('/media/move?name='+n+'&d='+b.dataset.m).then(loadThumbs)}});\n" +
        "  t.appendChild(e)})}).catch(function(){})}\n" +
        "function resize(file){return new Promise(function(res,rej){var img=new Image();img.onload=function(){var m=2560,w=img.naturalWidth,h=img.naturalHeight,s=Math.min(1,m/Math.max(w,h));var c=document.createElement('canvas');c.width=Math.round(w*s);c.height=Math.round(h*s);var x=c.getContext('2d');x.fillStyle='#fff';x.fillRect(0,0,c.width,c.height);x.imageSmoothingQuality='high';x.drawImage(img,0,0,c.width,c.height);URL.revokeObjectURL(img.src);c.toBlob(function(b){b?res(b):rej()},'image/jpeg',0.92)};img.onerror=function(){rej()};img.src=URL.createObjectURL(file)})}\n" +
        "function post(url,body,onProgress){return new Promise(function(res){var x=new XMLHttpRequest();x.open('POST',withTok(url));if(onProgress&&x.upload)x.upload.onprogress=function(e){if(e.lengthComputable)onProgress(Math.round(e.loaded*100/e.total))};\n" +
        " x.onload=function(){try{res(JSON.parse(x.responseText))}catch(e){res({ok:false,error:'보내기 실패'})}};x.onerror=function(){res({ok:false,error:'보내기 실패'})};x.send(body)})}\n" +
        "async function upload(files){var ok=0,err='';for(var i=0;i<files.length;i++){var f=files[i],label=(i+1)+'/'+files.length;\n" +
        " try{var isVid=/^video\\//.test(f.type);if(isVid&&f.size>300*1048576){err='동영상은 300MB 이하만';continue}\n" +
        "  msg((isVid?'동영상':'이미지')+' 보내는 중… '+label);var body=isVid?f:await resize(f);\n" +
        "  var s=await post('/media',body,isVid?function(p){msg('동영상 보내는 중… '+label+'  '+p+'%')}:null);\n" +
        "  if(s.auth){requestAuth();return}if(s.ok)ok++;else err=s.error}catch(e){err='보내기 실패'}}\n" +
        " msg(err?(ok+'개 추가 · 실패: '+err):('✔ '+ok+'개 추가됨'));loadThumbs()}\n" +
        "$('imgbox').addEventListener('toggle',function(){if(this.open)loadThumbs()});\n" +
        "// ── 알림음 · 목소리 ──\n" +
        "function loadVoices(){fetch('/voices',{cache:'no-store'}).then(function(r){return r.json()}).then(function(d){\n" +
        " var ch=$('chime');ch.innerHTML='';(d.chimes||[]).forEach(function(c){ch.add(new Option(c.label,c.name))});if(S.customChime)ch.add(new Option('직접 올린 소리','custom'));ch.value=S.chime||'dingdong';\n" +
        " var en=$('ttsEngine');en.innerHTML='';en.add(new Option('기본 음성 엔진 (폰 설정 따름)',''));(d.engines||[]).forEach(function(e){en.add(new Option(e.label,e.name))});en.value=S.ttsEngine||'';\n" +
        " var vo=$('voiceName');vo.innerHTML='';vo.add(new Option('기본 목소리',''));(d.voices||[]).forEach(function(x){vo.add(new Option(x.label,x.name))});vo.value=S.voiceName||'';\n" +
        " var vm=$('voiceMode');while(vm.options.length>2)vm.remove(2);(d.packs||[]).forEach(function(p){vm.add(new Option(p.label+' (녹음된 목소리 · 1~999번)',p.mode),1)});vm.value=S.voiceMode||'tts';\n" +
        " var ph=$('packPhrase');ph.innerHTML='';(d.phrases||[]).forEach(function(p){ph.add(new Option(p.label,p.name))});ph.value=S.packPhrase||'p_food';\n" +
        " for(var i=0;i<vm.options.length;i++)if(vm.options[i].value==='rec')vm.options[i].disabled=!S.recReady;voiceModeUi()}).catch(function(){})}\n" +
        "// 녹음된 목소리(승아 등)는 문장만 고르고, 기계 음성 설정(엔진·목소리·속도·높낮이)은 숨김\n" +
        "function voiceModeUi(){var pk=/^pack:/.test($('voiceMode').value);$('packbox').style.display=pk?'':'none';$('ttsbox').style.display=pk?'none':''}\n" +
        "$('soundbox').addEventListener('toggle',function(){if(this.open)loadVoices()});\n";
    private static final String P2 =
        "function previewVoice(){var a=new Audio(withTok('/voice/preview?n=51&voice='+encodeURIComponent($('voiceName').value)+'&rate='+$('rate').value+'&pitch='+$('pitch').value+'&mode='+encodeURIComponent($('voiceMode').value)+'&phrase='+$('packPhrase').value));msg('음성을 만드는 중…');a.onplaying=function(){msg('▶ 재생 중')};a.onerror=function(){msg('미리 듣기를 할 수 없습니다 (허용·음성 엔진 확인)')};a.play().catch(function(){})}\n" +
        "function saveSound(){var engineChanged=$('ttsEngine').value!==(S.ttsEngine||'');api('/config?'+['chime','voiceMode','ttsEngine','voiceName','rate','pitch','packPhrase'].map(q).join('&')).then(function(s){if(s&&s.ok){msg(engineChanged?'저장됨 · 음성 엔진이 바뀌어 목소리 목록을 다시 불러옵니다':'알림음·목소리 저장됨');if(engineChanged)setTimeout(loadVoices,2500)}})}\n" +
        "async function uploadChime(f){if(!f)return;if(f.size>5*1048576){msg('알림음 파일은 5MB 이하만');return}msg('알림음 보내는 중…');var s=await post('/chime/upload',f);if(s.auth){requestAuth();return}\n" +
        " if(s.ok){render(s);loadVoices();msg('✔ 내 소리 파일을 알림음으로 설정했습니다');new Audio('/chime?type=custom').play()}else msg(s.error||'올리지 못했습니다')}\n" +
        "// ── 영업 시간 · 마감 ──\n" +
        "function saveClosing(){api('/config?closeOn='+($('closeOn').checked?1:0)+'&'+['open','close','closeMsg'].map(q).join('&')).then(okMsg('영업 시간 저장됨'))}\n" +
        "function toggleClosed(){api('/closing?on='+(S.closed?0:1)).then(function(s){if(s&&s.ok)msg(s.closed?'🌙 마감을 안내했습니다':'마감 표시를 껐습니다')})}\n" +
        "// ── 화면 배치: 구역을 끌어서 옮기고(노란 모서리로 크기 조절) 저장 → TV·PC 화면에 반영 ──\n" +
        "var LAY={l:null,p:null},layTab='l',layLoaded=false,layBound=false,layDrag=null;\n" +
        "var LAY_NAMES={left:'이미지·동영상',hist:'최근 호출',wait:'조리중',logo:'로고'};\n" +
        "var LAY_PRESETS={\n" +
        " l:{basic:{left:[0,0,84,100],hist:[84,0,16,100]},\n" +
        "    flip:{left:[16,0,84,100],hist:[0,0,16,100]},\n" +
        "    big:{left:[0,0,70,100],hist:[70,0,30,100,1.25]}},\n" +
        " p:{basic:{left:[0,0,100,70],hist:[0,70,100,30]},\n" +
        "    flip:{left:[0,30,100,70],hist:[0,0,100,30]},\n" +
        "    big:{left:[0,0,100,78],hist:[0,78,100,22,1.25]}}};\n" +
        "function layFrom(t,name){var P=LAY_PRESETS[t][name],o={};\n" +
        " for(var k in P){var a=P[k];o[k]={x:a[0],y:a[1],w:a[2],h:a[3],on:true,s:a[4]||1}}\n" +
        " o.wait={x:o.hist.x,y:o.hist.y,w:o.hist.w,h:o.hist.h,on:false,s:1};\n" +
        " o.logo={x:84,y:0,w:16,h:20,on:false,s:1};return o}\n" +
        "function layFix(c){ // 예전 저장 배치 정리: '현재 호출'(cur) 구역은 없애고 로고 칸이 없으면 채움\n" +
        " if(c.cur)delete c.cur;\n" +
        " if(!c.logo)c.logo={x:84,y:0,w:16,h:20,on:false,s:1};return c}\n" +
        "function uploadLogo(f){if(!f)return;if(f.size>5*1048576){msg('로고 그림은 5MB 이하만');return}\n" +
        " $('logoNote').textContent='로고 보내는 중…';post('/logo/upload',f).then(function(s){if(s.auth){requestAuth();return}\n" +
        "  $('logoNote').textContent=s.ok?'로고 저장됨 (화면에 바로 반영됩니다)':(s.error||'보내기 실패')})}\n" +
        "function clearLogo(){post('/logo/clear',new Blob([])).then(function(s){if(s.auth){requestAuth();return}$('logoNote').textContent=s.ok?'로고를 지웠습니다':(s.error||'실패')})}\n" +
        "// ── 화면 색 ──\n" +
        "var COL_IDS={bg:'col_bg',panel:'col_panel',label:'col_label',cur:'col_cur',num:'col_num',card:'col_card'},colLoaded=false;\n" +
        "var COL_PRESETS={\n" +
        " vivid:{bg:'#000000',panel:'#101318',label:'#d5dbe2',cur:'#ffd400',num:'#ffffff',card:'#ff8a00'},\n" +
        " calm:{bg:'#111418',panel:'#1c2127',label:'#9aa3ad',cur:'#ffcc33',num:'#f2f2f2',card:'#ff8a00'},\n" +
        " cafe:{bg:'#1b120b',panel:'#2e1f14',label:'#e8d5b7',cur:'#ffb74d',num:'#fff3e0',card:'#a1673a'},\n" +
        " modern:{bg:'#000000',panel:'#14181f',label:'#9fb3c8',cur:'#ffd60a',num:'#ffffff',card:'#1f4e8c'}};\n" +
        "function colShow(c){for(var k in COL_IDS)$(COL_IDS[k]).value=c[k]}\n" +
        "function colPreset(name){colShow(COL_PRESETS[name])}\n" +
        "function saveColors(){var c={};for(var k in COL_IDS)c[k]=$(COL_IDS[k]).value;\n" +
        " api('/config?colors='+encodeURIComponent(JSON.stringify(c))).then(okMsg('화면 색 저장됨'))}\n" +
        "function resetColors(){api('/config?colors=').then(function(s){if(s&&s.ok!==false){colShow(COL_PRESETS.vivid);msg('기본 색으로 되돌렸습니다')}})}\n" +
        "function layPaint(k){var d=$('laycanvas').querySelector('.lb[data-k=\"'+k+'\"]');if(!d)return;var b=LAY[layTab][k];\n" +
        " d.style.left=b.x+'%';d.style.top=b.y+'%';d.style.width=b.w+'%';d.style.height=b.h+'%';d.classList.toggle('off',!b.on)}\n" +
        "function layList(){var L=LAY[layTab],h='';\n" +
        " for(var k in LAY_NAMES){var b=L[k];\n" +
        "  h+='<label><input type=\"checkbox\" data-on=\"'+k+'\"'+(b.on?' checked':'')+'> '+LAY_NAMES[k]+' 보이기 · 글자 <select data-sc=\"'+k+'\">'+\n" +
        "  [[0.8,'작게'],[1,'보통'],[1.25,'크게'],[1.5,'아주 크게']].map(function(o){return '<option value=\"'+o[0]+'\"'+(Math.abs((b.s||1)-o[0])<0.01?' selected':'')+'>'+o[1]+'</option>'}).join('')+'</select></label>'}\n" +
        " $('laylist').innerHTML=h}\n" +
        "function layRender(){var c=$('laycanvas');c.className=layTab==='p'?'p':'';c.style.aspectRatio=layTab==='p'?'9 / 16':'16 / 9';\n" +
        " c.innerHTML='';\n" +
        " for(var k in LAY_NAMES){var d=document.createElement('div');d.className='lb';d.dataset.k=k;d.innerHTML='<span>'+LAY_NAMES[k]+'</span><i></i>';c.appendChild(d);layPaint(k)}\n" +
        " $('lay_l').style.opacity=layTab==='l'?1:.5;$('lay_p').style.opacity=layTab==='p'?1:.5;\n" +
        " layList()}\n" +
        "function setLayTab(t){layTab=t;layRender()}\n" +
        "function layPreset(name){LAY[layTab]=layFrom(layTab,name);layRender()}\n" +
        "function layReset(){api('/config?layout=').then(function(s){if(s&&s.ok!==false){layLoaded=false;msg('기본 배치로 되돌렸습니다')}})}\n" +
        "function saveLayout(){api('/config?layout='+encodeURIComponent(JSON.stringify({l:LAY.l,p:LAY.p}))).then(okMsg('화면 배치 저장됨'))}\n" +
        "function layInit(){var c=$('laycanvas');\n" +
        " c.addEventListener('pointerdown',function(e){var d=e.target.closest?e.target.closest('.lb'):null;if(!d)return;var b=LAY[layTab][d.dataset.k],r=c.getBoundingClientRect();\n" +
        "  layDrag={k:d.dataset.k,rs:e.target.tagName==='I',sx:e.clientX,sy:e.clientY,b0:{x:b.x,y:b.y,w:b.w,h:b.h},W:r.width,H:r.height};\n" +
        "  c.appendChild(d); // 누른 구역을 맨 위로 올려서 겹쳐 있어도 잡히게\n" +
        "  try{d.setPointerCapture(e.pointerId)}catch(x){}e.preventDefault()});\n" +
        " c.addEventListener('pointermove',function(e){if(!layDrag)return;var g=layDrag,b=LAY[layTab][g.k],dx=(e.clientX-g.sx)/g.W*100,dy=(e.clientY-g.sy)/g.H*100;\n" +
        "  if(g.rs){b.w=Math.round(Math.max(5,Math.min(100-b.x,g.b0.w+dx)));b.h=Math.round(Math.max(5,Math.min(100-b.y,g.b0.h+dy)))}\n" +
        "  else{b.x=Math.round(Math.max(0,Math.min(100-b.w,g.b0.x+dx)));b.y=Math.round(Math.max(0,Math.min(100-b.h,g.b0.y+dy)))}\n" +
        "  layPaint(g.k)});\n" +
        " var end=function(){layDrag=null};c.addEventListener('pointerup',end);c.addEventListener('pointercancel',end);\n" +
        " $('laylist').addEventListener('change',function(e){var t=e.target,L=LAY[layTab];\n" +
        "  if(t.dataset.on){L[t.dataset.on].on=t.checked;layPaint(t.dataset.on)}\n" +
        "  if(t.dataset.sc){L[t.dataset.sc].s=+t.value}})}\n" +
        "// ── 키 설정 (호출기 앱과 공유, 키 이름은 KeyboardEvent.code) ──\n" +
        "var ACTS=[['call','호출'],['next','다음 번호 입력'],['recall','재호출'],['blank','화면 비우기'],['range','연속 호출 ~ (예: 1~4)'],['clear','입력 전체 삭제'],['back','한 칸 지우기']];\n" +
        "var KEY_TENKEY='call=NumpadEnter,Enter;next=NumpadAdd;recall=NumpadMultiply;blank=NumpadDivide;range=NumpadSubtract,Minus;clear=Escape;back=NumpadDecimal,NumpadComma,Backspace';\n" +
        "var KEY_BACKSPACE='call=NumpadEnter,Enter;next=NumpadAdd;recall=NumpadMultiply;blank=NumpadDivide;range=NumpadSubtract,Minus;clear=NumpadDecimal,NumpadComma,Escape;back=Backspace';\n" +
        "var KM=parseKeys(KEY_TENKEY),keysStr='',capturing=null;\n" +
        "function parseKeys(str){var m={};ACTS.forEach(function(a){m[a[0]]=[]});(str||'').split(';').forEach(function(p){var i=p.indexOf('=');if(i<1)return;var a=p.slice(0,i);if(!m[a])return;p.slice(i+1).split(',').forEach(function(c){if(c&&m[a].indexOf(c)<0)m[a].push(c)})});return m}\n" +
        "function serKeys(m){return ACTS.map(function(a){return a[0]+'='+m[a[0]].join(',')}).join(';')}\n" +
        "function keyLabel(c){var n={NumpadEnter:'텐키 Enter',NumpadAdd:'텐키 +',NumpadSubtract:'텐키 -',NumpadMultiply:'텐키 *',NumpadDivide:'텐키 /',NumpadDecimal:'텐키 .',NumpadComma:'텐키 ,',NumpadEqual:'텐키 =',Backspace:'← 백스페이스',Escape:'Esc',Delete:'Del',Space:'스페이스',Minus:'-',Equal:'=',Period:'.',Comma:',',Slash:'/',ArrowUp:'↑',ArrowDown:'↓',ArrowLeft:'←(화살표)',ArrowRight:'→'}[c];if(n)return n;\n" +
        " if(/^Numpad\\d$/.test(c))return '텐키 '+c.slice(6);if(/^Key[A-Z]$/.test(c))return c.slice(3);if(/^Digit\\d$/.test(c))return c.slice(5);return c.replace(/^Android:KEYCODE_/,'')}\n" +
        "function actionFor(c){for(var a in KM)if(KM[a].indexOf(c)>=0)return a;return null}\n" +
        "function saveKeys(){keysStr=serKeys(KM);renderKeys();api('/config?keys='+encodeURIComponent(keysStr)).then(okMsg('키 설정 저장됨'))}\n" +
        "function presetKeys(p){KM=parseKeys(p);capturing=null;saveKeys()}\n" +
        "function renderKeys(){var box=$('keyrows');if(!box)return;box.innerHTML='';ACTS.forEach(function(a){var row=document.createElement('div');row.className='krow';\n" +
        " var chips=KM[a[0]].map(function(c){return '<span class=\"chip\">'+keyLabel(c)+' <b data-a=\"'+a[0]+'\" data-c=\"'+c+'\">✕</b></span>'}).join('')||'<span class=\"none\">(없음)</span>';\n" +
        " row.innerHTML='<div class=\"kname\">'+a[1]+'</div><div class=\"kchips\">'+chips+'</div><button class=\"kset'+(capturing===a[0]?' on':'')+'\" data-set=\"'+a[0]+'\">'+(capturing===a[0]?'키를 누르세요…':'키 지정')+'</button>';box.appendChild(row)});\n" +
        " box.querySelectorAll('b[data-c]').forEach(function(x){x.onclick=function(){var l=KM[x.dataset.a];l.splice(l.indexOf(x.dataset.c),1);saveKeys()}});\n" +
        " box.querySelectorAll('button[data-set]').forEach(function(x){x.onclick=function(){capturing=capturing===x.dataset.set?null:x.dataset.set;renderKeys()}})}\n" +
        "var MODS=/^(Shift|Control|Alt|Meta)|^(NumLock|CapsLock|ScrollLock|Tab)$/;\n" +
        "document.addEventListener('keydown',function(e){\n" +
        " var code=e.code||'';\n" +
        " if(capturing){if(!code||MODS.test(code))return;e.preventDefault();if(e.repeat)return;for(var a in KM){var i=KM[a].indexOf(code);if(i>=0)KM[a].splice(i,1)}KM[capturing].push(code);var done=capturing;capturing=null;saveKeys();msg('\"'+keyLabel(code)+'\" → '+ACTS.filter(function(x){return x[0]===done})[0][1]);return}\n" +
        " var t=e.target.tagName;if(t==='INPUT'||t==='SELECT'||t==='TEXTAREA'||e.ctrlKey||e.altKey||e.metaKey)return;\n" +
        " var act=actionFor(code),m=act?null:/^(Numpad|Digit)([0-9])$/.exec(code);\n" +
        " if(!act&&!m)return; // 텐키는 NumLock 상태와 상관없이 키 위치(code)로 판단\n" +
        " e.preventDefault();if(document.activeElement&&document.activeElement.blur)document.activeElement.blur();\n" +
        " if(m){if(!e.repeat)window.k(m[2]);return}\n" +
        " if(act==='back')del();else if(act==='clear'){v='';show()}else if(e.repeat)return;\n" +
        " else if(act==='range')tilde();\n" +
        " else if(act==='call')callNum();else if(act==='next')next();\n" +
        " else if(act==='recall')api('/recall').then(okMsg('재호출했습니다'));\n" +
        " else if(act==='blank')api('/clear').then(okMsg('화면을 비웠습니다'));\n" +
        "});\n" +
        "renderKeys();\n" +
        "$('dispurl').textContent='http://'+location.host+'/display';\n" +
        "api('/status');setInterval(function(){fetch(withTok('/status'),{cache:'no-store'}).then(function(r){return r.json()}).then(render).catch(function(){})},3000);\n" +
        "</script></body></html>\n";

    static final String HTML = new StringBuilder(34307).append(P0).append(P1).append(P2).toString();
}
