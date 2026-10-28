package com.store.didcall;

/** PC 브라우저용 호출 화면 (/display) — tools/display.html 에서 자동 생성 (직접 수정하지 마세요) */
final class DisplayPage {
    private DisplayPage() {}

    static final String HTML =
        "<!doctype html>\n" +
        "<html lang=\"ko\"><head><meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
        "<title>DID 호출 화면</title>\n" +
        "<style>\n" +
        "*{box-sizing:border-box;margin:0;padding:0}\n" +
        "html,body{height:100%;overflow:hidden;background:#111418;color:#f2f2f2;font-family:'Malgun Gothic','Apple SD Gothic Neo','Noto Sans KR',sans-serif}\n" +
        "body.idle{cursor:none}\n" +
        ".wrap{position:fixed;inset:0;padding:3vh;display:flex;flex-direction:column}\n" +
        ".main{flex:1;display:flex;gap:3vh;min-height:0}\n" +
        ".left{flex:8;position:relative;background:#000;border-radius:2.4vh;overflow:hidden;min-width:0}\n" +
        ".left img{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;opacity:0;transition:opacity .7s}\n" +
        ".left.cover img{object-fit:cover}\n" +
        ".left img.on{opacity:1}\n" +
        "#ph{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;text-align:center;color:#9aa3ad;font-size:4vh;line-height:1.6;white-space:pre-line}\n" +
        ".right{flex:2;background:#1c2127;border-radius:2.4vh;padding:3vh 1.5vh;display:flex;flex-direction:column;align-items:center;min-width:0}\n" +
        ".lbl{color:#9aa3ad;font-weight:700;font-size:3.5vh;white-space:nowrap}\n" +
        "#cur{color:#ffcc33;font-weight:800;font-size:15vh;line-height:1.1;width:100%;text-align:center;white-space:nowrap;overflow:hidden}\n" +
        ".div{height:.3vh;background:#33404d;width:90%;margin:3vh 0 1.5vh}\n" +
        "#hist{flex:1;width:100%;display:flex;flex-direction:column;min-height:0}\n" +
        "#hist div{flex:1;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:7.5vh;white-space:nowrap;overflow:hidden}\n" +
        ".addr{color:#9aa3ad;font-size:2.3vh;padding-top:1.5vh;white-space:nowrap;overflow:hidden}\n" +
        ".addr.bad{color:#ff5a5a}\n" +
        "#ov{position:fixed;inset:0;background:rgba(0,0,0,.7);display:none;align-items:center;justify-content:center;opacity:0;transition:opacity .3s}\n" +
        "#ov.show{display:flex}\n" +
        "#ov.vis{opacity:1}\n" +
        "#card{min-width:55vw;max-width:94vw;background:#ff8a00;border-radius:4vh;padding:3vh 6vh 4.5vh;text-align:center;color:#fff;box-shadow:0 2vh 6vh rgba(0,0,0,.5)}\n" +
        "#card.y{background:#ffcc33;color:#111418}\n" +
        "#card.pop{animation:pop .45s cubic-bezier(.34,1.56,.64,1)}\n" +
        "@keyframes pop{from{transform:scale(.5)}to{transform:scale(1)}}\n" +
        "#pl{font-size:6vh;font-weight:700}\n" +
        "#pn{font-size:36vh;font-weight:800;line-height:1;white-space:nowrap}\n" +
        "#ps{font-size:5vh}\n" +
        "#start{position:fixed;inset:0;background:rgba(17,20,24,.94);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2vh;cursor:pointer;text-align:center}\n" +
        "#start[hidden]{display:none}\n" +
        "#start b{font-size:6vh;color:#ffcc33}\n" +
        "#start span{font-size:3vh;color:#9aa3ad;line-height:1.6}\n" +
        "</style></head><body>\n" +
        "<div class=\"wrap\">\n" +
        " <div class=\"main\">\n" +
        "  <div class=\"left\" id=\"left\"><img id=\"ia\" alt=\"\"><img id=\"ib\" alt=\"\"><div id=\"ph\">여기에 매장 이미지가 표시됩니다\n" +
        "휴대폰 브라우저 또는 호출기 앱에서 이미지를 추가하세요</div></div>\n" +
        "  <div class=\"right\">\n" +
        "   <div class=\"lbl\">현재 호출</div>\n" +
        "   <div id=\"cur\">—</div>\n" +
        "   <div class=\"div\"></div>\n" +
        "   <div class=\"lbl\">최근 호출</div>\n" +
        "   <div id=\"hist\"><div></div><div></div><div></div><div></div><div></div></div>\n" +
        "  </div>\n" +
        " </div>\n" +
        " <div class=\"addr\" id=\"addr\">연결 중…</div>\n" +
        "</div>\n" +
        "<div id=\"ov\"><div id=\"card\"><div id=\"pl\">호출 번호</div><div id=\"pn\"></div><div id=\"ps\"></div></div></div>\n" +
        "<div id=\"start\" hidden><b>화면을 클릭하면 시작합니다</b><span>소리(딩동·음성)를 켜고 전체화면으로 전환합니다.<br>윈도우 실행 파일(DID-Display.bat)로 열면 이 단계 없이 바로 시작됩니다.</span></div>\n" +
        "<script>\n" +
        "var S={},seq=null,imgs=[],imgKey='',idx=-1,front='ia',slideT=0,hideT=0,flashT=0,ac=null,voice=null,lastSlide=0,fails=0,speakT=0;\n" +
        "function $(i){return document.getElementById(i)}\n" +
        "// ── 소리 ──\n" +
        "function initAudio(){try{ac=ac||new (window.AudioContext||window.webkitAudioContext)();if(ac.state!=='running')ac.resume()}catch(e){}}\n" +
        "function synthChime(){var t0=ac.currentTime+.05;[[659.25,0],[523.25,.6]].forEach(function(n){[[1,.7],[2,.2],[3,.05]].forEach(function(h){var o=ac.createOscillator(),g=ac.createGain(),t=t0+n[1];o.frequency.value=n[0]*h[0];g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(h[1]*.6,t+.01);g.gain.exponentialRampToValueAtTime(.0001,t+1.4);o.connect(g);g.connect(ac.destination);o.start(t);o.stop(t+1.5)})})}\n" +
        "// 안드로이드 기기가 만든 소리 파일을 그대로 재생 (안드로이드 화면과 같은 소리)\n" +
        "var chimeBuf=null,voiceSrc=null,playId=0;\n" +
        "function loadBuf(url){return fetch(url,{cache:'no-store'}).then(function(r){if(!r.ok)throw 0;return r.arrayBuffer()}).then(function(a){return new Promise(function(ok,no){ac.decodeAudioData(a,ok,no)})})}\n" +
        "function playBuf(buf,done){var src=ac.createBufferSource();src.buffer=buf;src.connect(ac.destination);src.onended=done||null;src.start();return src}\n" +
        "function chime(){if(!ac||ac.state!=='running')return;if(chimeBuf){playBuf(chimeBuf);return}loadBuf('/chime.wav').then(function(b){chimeBuf=b;playBuf(b)}).catch(synthChime)}\n" +
        "function stopVoice(){playId++;try{if(voiceSrc){voiceSrc.onended=null;voiceSrc.stop()}}catch(e){}voiceSrc=null;if('speechSynthesis' in window)speechSynthesis.cancel()}\n" +
        "function announceVoice(n,text,rep){if(rep<=0)return;var id=++playId;\n" +
        " if(!S.voice||!ac||ac.state!=='running'){speak(text,rep);return}\n" +
        " loadBuf('/voice?n='+encodeURIComponent(n)).then(function(buf){if(id!==playId)return;(function play(k){if(k<=0||id!==playId)return;voiceSrc=playBuf(buf,function(){if(id===playId)speakT=setTimeout(function(){play(k-1)},800)})})(rep)}).catch(function(){if(id===playId)speak(text,rep)})}\n" +
        "function pickVoice(){if(!('speechSynthesis' in window))return;var v=speechSynthesis.getVoices().filter(function(x){return /^ko/i.test(x.lang)});voice=v.filter(function(x){return /Natural|Online/i.test(x.name)})[0]||v[0]||null}\n" +
        "if('speechSynthesis' in window){pickVoice();speechSynthesis.onvoiceschanged=pickVoice}\n" +
        "function speak(text,rep){if(!('speechSynthesis' in window)||rep<=0)return;speechSynthesis.cancel();(function say(k){if(k<=0)return;var u=new SpeechSynthesisUtterance(text);u.lang='ko-KR';if(voice)u.voice=voice;u.rate=.95;u.onend=function(){speakT=setTimeout(function(){say(k-1)},800)};speechSynthesis.speak(u)})(rep)}\n" +
        "// ── 글자 크기 맞춤 ──\n" +
        "function fit(el,vh){var s=vh;el.style.fontSize=s+'vh';while(s>2&&el.scrollWidth>el.clientWidth){s*=.92;el.style.fontSize=s+'vh'}}\n" +
        "// ── 상태 반영 ──\n" +
        "function render(s){\n" +
        " $('cur').textContent=s.current||'—';fit($('cur'),15);\n" +
        " var h=s.history||[],d=$('hist').children;\n" +
        " for(var i=0;i<d.length;i++){d[i].textContent=h[i]||'';d[i].style.opacity=1-i*.14;fit(d[i],7.5)}\n" +
        " $('left').classList.toggle('cover',s.fit==='cover');\n" +
        "}\n" +
        "function apply(s){\n" +
        " var prevSlide=S.slide;S=s;render(s);\n" +
        " if(seq===null){seq=s.seq}else if(s.seq!==seq){seq=s.seq;if(s.current)announce(s.current)}\n" +
        " if(!s.current)hidePopup();\n" +
        " if(prevSlide!==undefined&&prevSlide!==s.slide)schedule();\n" +
        " if(s.images!==imgs.length)loadImages();\n" +
        "}\n" +
        "function poll(){fetch('/status',{cache:'no-store'}).then(function(r){return r.json()}).then(function(s){fails=0;$('addr').className='addr';$('addr').textContent='● 연결됨  '+location.host+'   ·   전체화면 F11   ·   팝업 닫기 Esc';apply(s)}).catch(function(){if(++fails>=3){$('addr').className='addr bad';$('addr').textContent='● TV 기기('+location.host+')에 연결할 수 없습니다. 안드로이드 기기의 DID 앱이 켜져 있는지 확인하세요'}}).then(function(){setTimeout(poll,1000)})}\n" +
        "// ── 팝업 ──\n" +
        "function announce(n){\n" +
        " var c=$('card'),ov=$('ov');\n" +
        " $('pn').textContent=n;$('ps').textContent=S.sub||'';$('ps').style.display=(S.sub||'').trim()?'':'none';\n" +
        " clearTimeout(hideT);clearInterval(flashT);clearTimeout(speakT);\n" +
        " ov.classList.add('show');requestAnimationFrame(function(){ov.classList.add('vis')});\n" +
        " c.classList.remove('pop','y');void c.offsetWidth;c.classList.add('pop');\n" +
        " $('pn').style.fontSize=(n.length<=3?36:n.length==4?30:24)+'vh';\n" +
        " var k=6;setTimeout(function(){flashT=setInterval(function(){if(k--<=0){clearInterval(flashT);c.classList.remove('y');return}c.classList.toggle('y')},380)},500);\n" +
        " hideT=setTimeout(hidePopup,(S.popup||10)*1000);\n" +
        " chime();\n" +
        " var text=(S.tts||'{n}번 고객님').split('{n}').join(n);\n" +
        " stopVoice();var vid=playId;speakT=setTimeout(function(){if(vid===playId)announceVoice(n,text,S.repeat==null?2:S.repeat)},1300);\n" +
        "}\n" +
        "function hidePopup(){var ov=$('ov');if(!ov.classList.contains('show'))return;clearTimeout(hideT);clearInterval(flashT);$('card').classList.remove('y');ov.classList.remove('vis');setTimeout(function(){if(!ov.classList.contains('vis'))ov.classList.remove('show')},320)}\n" +
        "// ── 이미지 슬라이드쇼 ──\n" +
        "function loadImages(){fetch('/images',{cache:'no-store'}).then(function(r){return r.json()}).then(function(d){var key=d.images.join('|');if(key===imgKey)return;imgKey=key;imgs=d.images;idx=-1;$('ph').style.display=imgs.length?'none':'flex';if(!imgs.length){$('ia').classList.remove('on');$('ib').classList.remove('on');clearTimeout(slideT);return}next()}).catch(function(){})}\n" +
        "function next(){clearTimeout(slideT);if(!imgs.length)return;idx=(idx+1)%imgs.length;var src='/img/'+imgs[idx],back=front==='ia'?'ib':'ia',pre=new Image();pre.onload=function(){$(back).src=src;$(back).classList.add('on');$(front).classList.remove('on');front=back};pre.src=src;schedule()}\n" +
        "function schedule(){clearTimeout(slideT);if(imgs.length>1)slideT=setTimeout(next,(S.slide||8)*1000)}\n" +
        "// ── 시작 / 전체화면 / 커서 숨김 ──\n" +
        "function start(){initAudio();$('start').hidden=true;var e=document.documentElement;if(e.requestFullscreen&&!document.fullscreenElement)e.requestFullscreen().catch(function(){});if('speechSynthesis' in window){var u=new SpeechSynthesisUtterance(' ');u.volume=0;speechSynthesis.speak(u)}}\n" +
        "initAudio();\n" +
        "setTimeout(function(){if(!ac||ac.state!=='running')$('start').hidden=false},300);\n" +
        "$('start').onclick=start;\n" +
        "document.addEventListener('keydown',function(e){if(!$('start').hidden){start();return}if(e.key==='Escape')hidePopup();if(e.key==='ArrowRight')next()});\n" +
        "var idleT;document.addEventListener('mousemove',function(){document.body.classList.remove('idle');clearTimeout(idleT);idleT=setTimeout(function(){document.body.classList.add('idle')},3000)});\n" +
        "window.addEventListener('resize',function(){render(S)});\n" +
        "poll();loadImages();\n" +
        "</script></body></html>\n";
}
