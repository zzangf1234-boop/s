package com.store.didcall;

/** PC 브라우저용 호출 화면 (/display) — tools/display.html 에서 자동 생성 (직접 수정하지 마세요) */
final class DisplayPage {
    private DisplayPage() {}

    private static final String P0 =
        "<!doctype html>\n" +
        "<html lang=\"ko\"><head><meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
        "<title>DID Display</title>\n" +
        "<style>\n" +
        "*{box-sizing:border-box;margin:0;padding:0}\n" +
        "html,body{height:100%;overflow:hidden;background:#000;color:#fff;font-family:'Malgun Gothic','Apple SD Gothic Neo','Noto Sans KR',sans-serif}\n" +
        "body.idle{cursor:none}\n" +
        "/* ── 색: 선명(기본) ── */\n" +
        "body{--bg:#000;--panel:#101318;--line:#2b3138;--label:#d5dbe2;--cur:#ffd400;--num:#fff;--ph:#9aa3ad;--card:#ff8a00;--card2:#ffd400}\n" +
        "/* ── 색: 차분 ── */\n" +
        "body.calm{--bg:#111418;--panel:#1c2127;--line:#33404d;--label:#9aa3ad;--cur:#ffcc33;--num:#f2f2f2;--card:#ff8a00;--card2:#ffcc33}\n" +
        "body{background:var(--bg)}\n" +
        ".wrap{position:fixed;top:0;left:0;right:0;bottom:0;padding:1vh;display:flex;flex-direction:column}\n" +
        ".main{flex:1;display:flex;min-height:0}\n" +
        ".left{margin-right:1vh;flex:84;position:relative;background:#000;border-radius:1.2vh;overflow:hidden;min-width:0}\n" +
        ".left img,.left video{position:absolute;top:0;left:0;right:0;bottom:0;width:100%;height:100%;object-fit:contain;opacity:0;transition:opacity .7s;background:#000}\n" +
        ".left.cover img,.left.cover video{object-fit:cover}\n" +
        ".left img.on,.left video.on{opacity:1}\n" +
        "#ph{position:absolute;top:0;left:0;right:0;bottom:0;display:flex;align-items:center;justify-content:center;text-align:center;color:var(--ph);font-size:4vh;line-height:1.6;white-space:pre-line}\n" +
        ".right{flex:16;position:relative;background:var(--panel);border-radius:1.2vh;padding:2.5vh 1vh;display:flex;flex-direction:column;align-items:center;min-width:0}\n" +
        ".sec{width:100%;display:flex;flex-direction:column;align-items:center;min-height:0}\n" +
        "#secHist{flex:1;border-top:.3vh solid var(--line);margin-top:2vh;padding-top:1.5vh}\n" +
        ".lbl{color:var(--label);font-weight:700;font-size:3vh;white-space:nowrap}\n" +
        "#cur{color:var(--cur);font-weight:800;font-size:13vh;line-height:1.1;width:100%;text-align:center;white-space:nowrap;overflow:hidden}\n" +
        "#hist{flex:1;width:100%;display:flex;flex-direction:column;min-height:0}\n" +
        "#hist div{flex:1;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:6.5vh;white-space:nowrap;overflow:hidden;color:var(--num)}\n" +
        "#hist div:nth-child(n+6){display:none}\n" +
        "body.calm #hist div:nth-child(2){opacity:.86}body.calm #hist div:nth-child(3){opacity:.72}body.calm #hist div:nth-child(4){opacity:.58}body.calm #hist div:nth-child(5){opacity:.44}\n" +
        "#dot{position:absolute;top:1.2vh;right:1.2vh;width:1.4vh;height:1.4vh;border-radius:50%;background:#666}\n" +
        "#dot.ok{background:#4cd964}\n" +
        "#dot.bad{background:#ff5a5a;animation:blink 1s infinite}\n" +
        "@keyframes blink{50%{opacity:.2}}\n" +
        "/* ── 세로 화면: 위 이미지, 아래 현재 호출 | 최근 호출(2열) ── */\n" +
        "@media (orientation:portrait){\n" +
        " .main{flex-direction:column}\n" +
        " .left{margin-right:0;margin-bottom:1vh;flex:70}\n" +
        " .right{flex:30;flex-direction:row;align-items:stretch;padding:1.5vh 1.5vw}\n" +
        " .sec{justify-content:center}\n" +
        " #secCur{flex:42}\n" +
        " #secHist{flex:58;border-top:0;margin-top:0;padding-top:0;border-left:.3vh solid var(--line);margin-left:1.5vw;padding-left:1.5vw}\n" +
        " .lbl{font-size:2.4vh}\n" +
        " #cur{font-size:12vh}\n" +
        " #hist{display:grid;grid-template-columns:1fr 1fr;grid-auto-rows:1fr}\n" +
        " #hist div{font-size:5vh}\n" +
        " #hist div:nth-child(n+6){display:flex}\n" +
        " /* 세로 팝업: 가로 화면 팝업과 비슷한 실제 크기 */\n" +
        " #card{min-width:56vw!important;max-width:80vw!important;padding:1.4vh 3vw 2vh!important;border-radius:1.8vh!important}\n" +
        " #pl{font-size:2.6vh!important}\n" +
        " #ps{font-size:2.2vh!important}\n" +
        "}\n" +
        "/* ── 호출 팝업 ── */\n" +
        "#ov{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.7);display:none;align-items:center;justify-content:center;opacity:0;transition:opacity .3s;z-index:20}\n" +
        "#ov.show{display:flex}\n" +
        "#ov.vis{opacity:1}\n" +
        "#card{min-width:37vw;max-width:63vw;background:var(--card);border-radius:2.7vh;padding:2vh 4vh 3vh;text-align:center;color:#fff;box-shadow:0 2vh 6vh rgba(0,0,0,.5)}\n" +
        "#card.y{background:var(--card2);color:#111418}\n" +
        "#card.pop{animation:pop .45s cubic-bezier(.34,1.56,.64,1)}\n" +
        "@keyframes pop{from{transform:scale(.5)}to{transform:scale(1)}}\n" +
        "#pl{font-size:4vh;font-weight:700}\n" +
        "#pn{font-size:24vh;font-weight:800;line-height:1;white-space:nowrap;overflow:hidden;width:100%}\n" +
        "#ps{font-size:3.3vh}\n" +
        "/* ── 영업 마감 ── */\n" +
        "#closed{position:fixed;top:0;left:0;right:0;bottom:0;z-index:15;background:rgba(0,0,0,.88);display:none;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:6vh}\n" +
        "#closed.show{display:flex}\n" +
        "#closed .ic{font-size:10vh;margin-bottom:3vh}\n" +
        "#closed .msg{font-size:6vh;font-weight:800;line-height:1.4;color:#fff;white-space:pre-line;max-width:90vw}\n" +
        "#closed .sub{font-size:3vh;color:var(--label);margin-top:3vh}\n" +
        "/* ── 시작 / 메뉴 ── */\n" +
        "#start{position:fixed;top:0;left:0;right:0;bottom:0;z-index:40;background:rgba(17,20,24,.94);display:flex;flex-direction:column;align-items:center;justify-content:center;cursor:pointer;text-align:center}\n" +
        "#start[hidden]{display:none}\n" +
        "#start b{font-size:6vh;color:#ffcc33}\n" +
        "#start span{margin-top:2vh;font-size:3vh;color:#9aa3ad;line-height:1.6}\n" +
        "#corner{position:fixed;top:0;left:0;width:9vh;height:9vh;z-index:50}\n" +
        "#menu{position:fixed;top:0;left:0;right:0;bottom:0;z-index:60;background:rgba(0,0,0,.6);display:none;align-items:center;justify-content:center}\n" +
        "#menu.show{display:flex}\n" +
        "#menu .box{background:#1c2127;border-radius:2vh;padding:3vh;text-align:center;min-width:40vh;max-width:90vw;box-shadow:0 2vh 6vh rgba(0,0,0,.5)}\n" +
        "#menu .t{color:#9aa3ad;font-size:2.6vh;margin-bottom:2vh}\n" +
        "#menu button{display:block;width:100%;margin-top:1.4vh;padding:2.2vh 4vh;border:0;border-radius:1.4vh;font-size:3.4vh;font-weight:700;color:#fff;background:#33404d;cursor:pointer;font-family:inherit}\n" +
        "#menu button:hover{filter:brightness(1.25)}\n" +
        "#menu button.x{background:#262b31;color:#9aa3ad;font-size:2.8vh}\n" +
        "#menu .m{color:#ffcc33;font-size:2.4vh;margin-top:1.6vh;min-height:3vh}\n" +
        "#menu .info{color:#9aa3ad;font-size:2.2vh;margin-top:1.6vh;line-height:1.5}\n" +
        "</style></head><body>\n" +
        "<div class=\"wrap\">\n" +
        " <div class=\"main\">\n" +
        "  <div class=\"left\" id=\"left\"><img id=\"ia\" alt=\"\"><img id=\"ib\" alt=\"\"><video id=\"vv\" playsinline muted preload=\"auto\"></video><div id=\"ph\">여기에 매장 이미지·동영상이 표시됩니다\n" +
        "휴대폰 브라우저 또는 호출기 앱에서 추가하세요</div></div>\n" +
        "  <div class=\"right\">\n" +
        "   <div id=\"dot\" title=\"호출기 연결 상태\"></div>\n" +
        "   <div class=\"sec\" id=\"secCur\"><div class=\"lbl\">현재 호출</div><div id=\"cur\">—</div></div>\n" +
        "   <div class=\"sec\" id=\"secHist\"><div class=\"lbl\">최근 호출</div><div id=\"hist\"><div></div><div></div><div></div><div></div><div></div><div></div></div></div>\n" +
        "  </div>\n" +
        " </div>\n" +
        "</div>\n" +
        "<div id=\"closed\"><div class=\"ic\">🌙</div><div class=\"msg\" id=\"closedMsg\"></div><div class=\"sub\" id=\"closedSub\"></div></div>\n" +
        "<div id=\"ov\"><div id=\"card\"><div id=\"pl\">호출 번호</div><div id=\"pn\"></div><div id=\"ps\"></div></div></div>\n" +
        "<div id=\"corner\"></div>\n" +
        "<div id=\"menu\"><div class=\"box\"><div class=\"t\">호출 화면 메뉴</div>\n" +
        "<button id=\"mReload\">새로고침</button><button id=\"mRot\">화면 회전 (세로 거치)</button><button id=\"mMin\">최소화</button><button id=\"mExit\">종료</button><button id=\"mCancel\" class=\"x\">닫기</button>\n" +
        "<div class=\"m\" id=\"mMsg\"></div><div class=\"info\" id=\"mInfo\"></div></div></div>\n" +
        "<div id=\"start\" hidden><b>화면을 클릭하면 시작합니다</b><span>소리(딩동·음성)를 켜고 전체화면으로 전환합니다.<br>윈도우 실행 파일(DID-Display.bat)로 열면 이 단계 없이 바로 시작됩니다.</span></div>\n" +
        "<script>\n" +
        "// ── 화면 회전 (세로로 단 TV): 회전이 설정돼 있으면 이 페이지를 돌린 틀(iframe) 안에 다시 띄움 ──\n" +
        "var PARAMS=location.search,APP=/[?&]app=1/.test(PARAMS),INNER=/[?&]inner=1/.test(PARAMS);\n" +
        "function getRot(){var m=/[?&]rot=(0|90|270)/.exec(PARAMS);if(m)return +m[1];try{return +(localStorage.getItem('didRot')||0)}catch(e){return 0}}\n" +
        "function setRot(r){if(window.top!==window&&window.top.setRot){window.top.setRot(r);return}try{localStorage.setItem('didRot',String(r))}catch(e){}location.href='/display'+(APP?'?app=1':'')}\n" +
        "var ROT=INNER?0:getRot();\n" +
        "if(ROT===90||ROT===270){\n" +
        " document.body.innerHTML='';document.body.style.background='#000';\n" +
        " var f=document.createElement('iframe');f.src='/display?inner=1'+(APP?'&app=1':'');f.setAttribute('allow','autoplay');\n" +
        " f.style.cssText='position:fixed;top:0;left:0;border:0;width:100vh;height:100vw;transform-origin:0 0;transform:'+(ROT===90?'translateX(100vw) rotate(90deg)':'translateY(100vh) rotate(-90deg)');\n" +
        " document.body.appendChild(f);\n" +
        " window.next=function(){try{f.contentWindow.next()}catch(e){}};\n" +
        "} else (function(){\n" +
        "var S={},seq=null,closeSeq=null,items=[],mediaVer=null,idx=-1,front='ia',slideT=0,hideT=0,flashT=0,ac=null,voice=null,fails=0,speakT=0;\n" +
        "function $(i){return document.getElementById(i)}\n" +
        "// ── 소리: 호출기 폰이 만든 알림음·음성 파일을 그대로 재생 (TV·PC 모두 같은 소리) ──\n" +
        "function initAudio(){try{ac=ac||new (window.AudioContext||window.webkitAudioContext)();if(ac.state!=='running')ac.resume()}catch(e){}}\n" +
        "function synthChime(){var t0=ac.currentTime+.05;[[659.25,0],[523.25,.6]].forEach(function(n){[[1,.7],[2,.2],[3,.05]].forEach(function(h){var o=ac.createOscillator(),g=ac.createGain(),t=t0+n[1];o.frequency.value=n[0]*h[0];g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(h[1]*.6,t+.01);g.gain.exponentialRampToValueAtTime(.0001,t+1.4);o.connect(g);g.connect(ac.destination);o.start(t);o.stop(t+1.5)})})}\n" +
        "var chimeBuf=null,chimeKey='',voiceSrc=null,playId=0;\n" +
        "function loadBuf(url){return fetch(url,{cache:'no-store'}).then(function(r){if(!r.ok)throw 0;return r.arrayBuffer()}).then(function(a){return new Promise(function(ok,no){ac.decodeAudioData(a,ok,no)})})}\n" +
        "function playBuf(buf,done){var src=ac.createBufferSource();src.buffer=buf;src.connect(ac.destination);src.onended=done||null;src.start();return src}\n" +
        "function chime(){if(!ac||ac.state!=='running')return;var key=(S.chime||'')+'/'+(S.chimeVer||0);if(chimeBuf&&key===chimeKey){playBuf(chimeBuf);return}\n" +
        " loadBuf('/chime?v='+encodeURIComponent(key)).then(function(b){chimeBuf=b;chimeKey=key;playBuf(b)}).catch(synthChime)}\n" +
        "function stopVoice(){playId++;try{if(voiceSrc){voiceSrc.onended=null;voiceSrc.stop()}}catch(e){}voiceSrc=null;if('speechSynthesis' in window)speechSynthesis.cancel()}\n" +
        "function playVoice(url,text,rep){if(rep<=0)return;var id=++playId;\n" +
        " if(!S.voice||!ac||ac.state!=='running'){speak(text,rep);return}\n" +
        " loadBuf(url).then(function(buf){if(id!==playId)return;(function play(k){if(k<=0||id!==playId)return;voiceSrc=playBuf(buf,function(){if(id===playId)speakT=setTimeout(function(){play(k-1)},800)})})(rep)}).catch(function(){if(id===playId)speak(text,rep)})}\n" +
        "function pickVoice(){if(!('speechSynthesis' in window))return;var v=speechSynthesis.getVoices().filter(function(x){return /^ko/i.test(x.lang)});voice=v.filter(function(x){return /Natural|Online/i.test(x.name)})[0]||v[0]||null}\n" +
        "if('speechSynthesis' in window){pickVoice();speechSynthesis.onvoiceschanged=pickVoice}\n" +
        "function speak(text,rep){if(!('speechSynthesis' in window)||rep<=0)return;speechSynthesis.cancel();(function say(k){if(k<=0)return;var u=new SpeechSynthesisUtterance(text);u.lang='ko-KR';if(voice)u.voice=voice;u.rate=.95;u.onend=function(){speakT=setTimeout(function(){say(k-1)},800)};speechSynthesis.speak(u)})(rep)}\n" +
        "// ── 글자 크기 맞춤 ──\n" +
        "function fit(el,vh){var s=vh;el.style.fontSize=s+'vh';while(s>2&&el.scrollWidth>el.clientWidth){s*=.92;el.style.fontSize=s+'vh'}}\n" +
        "function portrait(){return window.innerHeight>window.innerWidth}\n" +
        "// ── 상태 반영 ──\n" +
        "function render(s){\n" +
        " document.body.classList.toggle('calm',s.theme==='calm');\n" +
        " $('cur').textContent=s.current||'—';fit($('cur'),portrait()?12:13);\n" +
        " var h=s.history||[],d=$('hist').children;\n" +
        " for(var i=0;i<d.length;i++){d[i].textContent=h[i]||'';fit(d[i],portrait()?5:6.5)}\n" +
        " $('left').classList.toggle('cover',s.fit==='cover');\n" +
        " $('closedMsg').textContent=s.closeMsg||'';\n" +
        " $('closedSub').textContent=s.closeOn&&s.open?('영업 시간  '+s.open+' ~ '+s.close):'';\n" +
        " $('closed').classList.toggle('show',!!s.closed);\n" +
        " $('vv').muted=!s.videoSound;\n" +
        "}\n" +
        "function apply(s){\n" +
        " var prevSlide=S.slide;S=s;render(s);\n" +
        " if(seq===null){seq=s.seq}else if(s.seq!==seq){seq=s.seq;if(s.current)announce(s.current)}\n" +
        " if(closeSeq===null){closeSeq=s.closeSeq}else if(s.closeSeq!==closeSeq){closeSeq=s.closeSeq;if(s.closed)announceClosing()}\n" +
        " if(!s.current)hidePopup();\n" +
        " if(prevSlide!==undefined&&prevSlide!==s.slide)schedule();\n";
    private static final String P1 =
        " if(s.mediaVer!==mediaVer){mediaVer=s.mediaVer;loadMedia()}\n" +
        "}\n" +
        "function poll(){fetch('/status?d=1',{cache:'no-store'}).then(function(r){return r.json()}).then(function(s){fails=0;$('dot').className='ok';apply(s)}).catch(function(){if(++fails>=3){$('dot').className='bad'}}).then(function(){setTimeout(poll,1000)})}\n" +
        "// ── 호출 팝업 ──\n" +
        "function announce(n){\n" +
        " var c=$('card'),ov=$('ov');\n" +
        " $('pn').textContent=n;$('ps').textContent=S.sub||'';$('ps').style.display=(S.sub||'').trim()?'':'none';\n" +
        " clearTimeout(hideT);clearInterval(flashT);clearTimeout(speakT);\n" +
        " ov.classList.add('show');requestAnimationFrame(function(){ov.classList.add('vis')});\n" +
        " c.classList.remove('pop','y');void c.offsetWidth;c.classList.add('pop');\n" +
        " var base=portrait()?14:24;fit($('pn'),n.length<=3?base:n.length==4?base*.84:base*.67);\n" +
        " var k=6;setTimeout(function(){flashT=setInterval(function(){if(k--<=0){clearInterval(flashT);c.classList.remove('y');return}c.classList.toggle('y')},380)},500);\n" +
        " hideT=setTimeout(hidePopup,(S.popup||10)*1000);\n" +
        " duck(true);\n" +
        " chime();\n" +
        " var text=(S.tts||'{n}번 고객님').split('{n}').join(n);\n" +
        " stopVoice();var vid=playId;speakT=setTimeout(function(){if(vid===playId)playVoice('/voice?n='+encodeURIComponent(n),text,S.repeat==null?2:S.repeat)},1300);\n" +
        "}\n" +
        "function hidePopup(){var ov=$('ov');if(!ov.classList.contains('show'))return;clearTimeout(hideT);clearInterval(flashT);$('card').classList.remove('y');ov.classList.remove('vis');duck(false);setTimeout(function(){if(!ov.classList.contains('vis'))ov.classList.remove('show')},320)}\n" +
        "// 동영상 소리를 켠 경우 호출 안내 중에는 작게\n" +
        "function duck(on){var v=$('vv');try{v.volume=on?.12:1}catch(e){}}\n" +
        "// ── 영업 마감 안내 ──\n" +
        "function announceClosing(){hidePopup();chime();stopVoice();var vid=playId;speakT=setTimeout(function(){if(vid===playId)playVoice('/voice/closing?v='+closeSeq,S.closeMsg||'',1)},1300)}\n" +
        "// ── 이미지·동영상 슬라이드쇼 ──\n" +
        "function loadMedia(){fetch('/media',{cache:'no-store'}).then(function(r){return r.json()}).then(function(d){\n" +
        " var list=d.items||[];var key=list.map(function(x){return x.name}).join('|');\n" +
        " if(key===items.map(function(x){return x.name}).join('|'))return;\n" +
        " items=list;idx=-1;$('ph').style.display=items.length?'none':'flex';\n" +
        " if(!items.length){$('ia').classList.remove('on');$('ib').classList.remove('on');stopVideo();clearTimeout(slideT);return}\n" +
        " next()}).catch(function(){})}\n" +
        "function stopVideo(){var v=$('vv');v.classList.remove('on');v.onended=null;v.onerror=null;try{v.pause()}catch(e){}}\n" +
        "function next(){clearTimeout(slideT);if(!items.length)return;idx=(idx+1)%items.length;var it=items[idx],src='/m/'+it.name;\n" +
        " if(it.video){showVideo(src);return}\n" +
        " var back=front==='ia'?'ib':'ia',pre=new Image();\n" +
        " pre.onload=function(){$(back).src=src;$(back).classList.add('on');$(front).classList.remove('on');front=back;stopVideo()};\n" +
        " pre.onerror=function(){schedule()};pre.src=src;schedule()}\n" +
        "window.next=next;\n" +
        "function showVideo(src){var v=$('vv');v.muted=!S.videoSound;v.loop=items.length===1;\n" +
        " v.onended=function(){next()};v.onerror=function(){slideT=setTimeout(next,1000)};\n" +
        " if(v.getAttribute('src')!==src){v.src=src}else{try{v.currentTime=0}catch(e){}}\n" +
        " var p=v.play();if(p&&p.catch)p.catch(function(){v.muted=true;var p2=v.play();if(p2&&p2.catch)p2.catch(function(){slideT=setTimeout(next,3000)})});\n" +
        " v.classList.add('on');$('ia').classList.remove('on');$('ib').classList.remove('on')}\n" +
        "function schedule(){clearTimeout(slideT);if(items.length>1)slideT=setTimeout(next,(S.slide||8)*1000)}\n" +
        "// ── 시작 / 전체화면 / 커서 숨김 ──\n" +
        "function start(){initAudio();$('start').hidden=true;var e=document.documentElement;try{if(!APP&&!INNER&&e.requestFullscreen&&!document.fullscreenElement){var fp=e.requestFullscreen();if(fp&&fp.catch)fp.catch(function(){})}}catch(x){}if('speechSynthesis' in window){var u=new SpeechSynthesisUtterance(' ');u.volume=0;speechSynthesis.speak(u)}var v=$('vv');if(v.classList.contains('on')&&v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){})}}\n" +
        "initAudio();\n" +
        "setTimeout(function(){if(!ac||ac.state!=='running')$('start').hidden=false},300);\n" +
        "$('start').onclick=start;\n" +
        "document.addEventListener('keydown',function(e){if(!$('start').hidden){start();return}if(e.key==='Escape')hidePopup();if(e.key==='ArrowRight')next()});\n" +
        "var idleT;document.addEventListener('mousemove',function(){document.body.classList.remove('idle');clearTimeout(idleT);idleT=setTimeout(function(){document.body.classList.add('idle')},3000)});\n" +
        "window.addEventListener('resize',function(){render(S)});\n" +
        "// ── 숨은 메뉴: 왼쪽 위 모서리를 빠르게 3번 클릭 → 새로고침 / 회전 / 최소화 / 종료 ──\n" +
        "var cornerHits=[],menuT=0;\n" +
        "$('corner').addEventListener('click',function(){var now=Date.now();cornerHits.push(now);cornerHits=cornerHits.filter(function(t){return now-t<1500});if(cornerHits.length>=3){cornerHits=[];openMenu()}});\n" +
        "function screenInfo(){var r=window.devicePixelRatio||1,w=window.innerWidth,h=window.innerHeight;\n" +
        " return '화면 정보: '+Math.round(w*r)+'×'+Math.round(h*r)+' 픽셀 (배율 '+(Math.round(r*100)/100)+')'+(Math.max(w*r,h*r)<1600?'  ⚠ 720p 이하로 그리는 기기 — 흐려 보일 수 있음':'')}\n" +
        "function openMenu(){$('mMsg').textContent='';$('mInfo').textContent=screenInfo()+'  ·  회전 '+getRot()+'°';\n" +
        " $('mMin').style.display=APP?'none':'';$('mExit').style.display=APP?'none':'';$('menu').classList.add('show');clearTimeout(menuT);menuT=setTimeout(closeMenu,15000)}\n" +
        "function closeMenu(){clearTimeout(menuT);$('menu').classList.remove('show')}\n" +
        "function helper(cmd){return fetch('http://127.0.0.1:8799/'+cmd+'?t='+Date.now(),{mode:'no-cors',cache:'no-store'})}\n" +
        "$('mCancel').onclick=closeMenu;\n" +
        "$('menu').addEventListener('click',function(e){if(e.target===this)closeMenu()});\n" +
        "$('mReload').onclick=function(){(window.top!==window?window.top:window).location.reload()};\n" +
        "$('mRot').onclick=function(){var r=getRot();setRot(r===0?90:r===90?270:0)};\n" +
        "$('mMin').onclick=function(){helper('min').then(closeMenu).catch(function(){\n" +
        " if(document.fullscreenElement&&document.exitFullscreen){document.exitFullscreen();closeMenu();return}\n" +
        " $('mMsg').textContent='최소화는 윈도우 실행 파일(DID-Display.bat)로 열었을 때 됩니다'})};\n" +
        "$('mExit').onclick=function(){helper('close').then(function(){setTimeout(function(){$('mMsg').textContent='종료되지 않으면 Alt+F4 를 누르세요'},1500)}).catch(function(){\n" +
        " try{window.top.close()}catch(x){}setTimeout(function(){$('mMsg').textContent='Alt+F4 를 누르면 종료됩니다'},400)})};\n" +
        "document.addEventListener('keydown',function(e){if(e.key==='Escape'&&$('menu').classList.contains('show'))closeMenu()},true);\n" +
        "window.screenInfo=screenInfo;\n" +
        "poll();\n" +
        "})();\n" +
        "</script></body></html>\n";

    static final String HTML = new StringBuilder(18441).append(P0).append(P1).toString();
}
