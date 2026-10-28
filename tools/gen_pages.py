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
    # 자바 문자열 상수는 64KB 제한이 있어 약 12,000자씩 나눠 담고 실행 시 이어 붙임
    chunks, cur, size = [], [], 0
    for l in lines:
        if cur and size + len(l) > 12000:
            chunks.append(cur); cur, size = [], 0
        cur.append(l); size += len(l) + 1
    if cur: chunks.append(cur)
    parts = []
    for i, ch in enumerate(chunks):
        body = " +\n        ".join(json.dumps(l + eol, ensure_ascii=False) for l in ch)
        parts.append(f"    private static final String P{i} =\n        {body};\n")
    joined = " + ".join(f"P{i}" for i in range(len(chunks)))
    java = f"""package com.store.didcall;

/** {doc} — tools/{src} 에서 자동 생성 (직접 수정하지 마세요) */
final class {cls} {{
    private {cls}() {{}}

{"".join(parts)}
    static final String HTML = new StringBuilder({len(html) + 16}).append({joined.replace(" + ", ").append(")}).toString();
}}
"""
    open(os.path.join(root, "app/src/main/java/com/store/didcall", cls + ".java"), "w", encoding="utf-8").write(java)
    print(cls, len(html), "chars")
