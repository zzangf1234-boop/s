"""tools/*.html 을 앱에 포함되는 Java 문자열 상수로 변환합니다.  사용법: python3 tools/gen_pages.py"""
import json, os
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
pages = [("web.html", "WebPage", "휴대폰 브라우저 호출·관리 페이지 (/)", "\n"),
         ("display.html", "DisplayPage", "PC 브라우저용 호출 화면 (/display)", "\n"),
         ("display-launcher.bat", "LauncherBat", "윈도우 실행 파일 (/display.bat), __URL__ 은 접속 주소로 바뀜", "\r\n")]
for src, cls, doc, eol in pages:
    html = open(os.path.join(root, "tools", src), encoding="utf-8").read().replace("\r\n", "\n")
    keep_blank = eol == "\r\n"
    lines = [l for l in html.split("\n") if keep_blank or l != ""]
    if keep_blank and lines and lines[-1] == "": lines.pop()
    if src.endswith(".bat"): assert all(ord(ch) < 128 for ch in html), "bat must be ASCII"
    body = " +\n        ".join(json.dumps(l + eol, ensure_ascii=False) for l in lines)
    java = f"""package com.store.didcall;

/** {doc} — tools/{src} 에서 자동 생성 (직접 수정하지 마세요) */
final class {cls} {{
    private {cls}() {{}}

    static final String HTML =
        {body};
}}
"""
    open(os.path.join(root, "app/src/main/java/com/store/didcall", cls + ".java"), "w", encoding="utf-8").write(java)
    print(cls, len(html), "chars")
