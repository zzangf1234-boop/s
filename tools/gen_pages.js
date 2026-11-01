// tools/*.html 을 앱에 포함되는 Java 문자열 상수로 변환합니다.  사용법: node tools/gen_pages.js
// (gen_pages.py 와 같은 동작 — 이 컴퓨터에는 파이썬 대신 node 가 있어서 만든 것)
const fs = require('fs'), path = require('path');
const root = path.dirname(__dirname);
const pages = [
  ["web.html", "WebPage", "휴대폰 브라우저 호출·관리 페이지 (/)", "\n"],
  ["display.html", "DisplayPage", "PC 브라우저용 호출 화면 (/display)", "\n"],
  ["display-launcher.bat", "LauncherBat", "윈도우 실행 파일 (/display.bat), __URL__ 은 접속 주소로 바뀜", "\r\n"]];
for (const [src, cls, doc, eol] of pages) {
  const html = fs.readFileSync(path.join(root, "tools", src), "utf8").replace(/\r\n/g, "\n");
  const keepBlank = eol === "\r\n";
  let lines = html.split("\n").filter(l => keepBlank || l !== "");
  if (keepBlank && lines.length && lines[lines.length - 1] === "") lines.pop();
  if (src.endsWith(".bat") && !/^[\x00-\x7F]*$/.test(html)) throw new Error("bat must be ASCII");
  // 자바 문자열 상수는 64KB 제한이 있어 약 12,000자씩 나눠 담고 실행 시 이어 붙임
  const chunks = [];
  let cur = [], size = 0;
  for (const l of lines) {
    if (cur.length && size + l.length > 12000) { chunks.push(cur); cur = []; size = 0; }
    cur.push(l); size += l.length + 1;
  }
  if (cur.length) chunks.push(cur);
  const parts = chunks.map((ch, i) =>
    "    private static final String P" + i + " =\n        " +
    ch.map(l => JSON.stringify(l + eol)).join(" +\n        ") + ";\n");
  const joined = chunks.map((_, i) => "P" + i).join(").append(");
  const java = `package com.store.didcall;

/** ${doc} — tools/${src} 에서 자동 생성 (직접 수정하지 마세요) */
final class ${cls} {
    private ${cls}() {}

${parts.join("")}
    static final String HTML = new StringBuilder(${html.length + 16}).append(${joined}).toString();
}
`;
  fs.writeFileSync(path.join(root, "app/src/main/java/com/store/didcall", cls + ".java"), java, "utf8");
  console.log(cls, html.length, "chars");
}
