// 각 메서드의 시작/끝 줄, 줄 수, 제어문(if/for/while/else-if) 중첩 깊이 측정
const fs = require('fs');
const path = require('path');

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? walk(path.join(dir, entry.name)) : entry.name.endsWith('.java') ? [path.join(dir, entry.name)] : []);
}

const root = process.argv[2];
const rows = [];
for (const file of walk(root)) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  for (let index = 0; index < lines.length; index++) {
    const header = lines[index].match(/^    (public|private|protected)[^=;]*\s(\w+)\s*\(/);
    if (!header || /\bclass\b|\binterface\b/.test(lines[index])) continue;
    // 메서드 본문 범위 찾기 (주석·문자열 안 중괄호는 제거 후 계산)
    let depth = 0, started = false, end = index;
    const controlStack = []; let maxControl = 0;
    for (let cursor = index; cursor < lines.length; cursor++) {
      const code = lines[cursor].replace(/\/\/.*$/, '').replace(/"(\\.|[^"\\])*"/g, '""');
      // 제어문으로 여는 블록인지 판별
      const isControl = /\b(if|for|while|switch)\s*\(|\belse\b/.test(code);
      for (const character of code) {
        if (character === '{') {
          depth++; started = true;
          controlStack.push(isControl && depth > 1);
          maxControl = Math.max(maxControl, controlStack.filter(Boolean).length);
        } else if (character === '}') {
          depth--; controlStack.pop();
        }
      }
      // 중괄호 없는 한 줄 if (예: if (x) return;) 도 깊이 1 로 반영
      if (started && /\bif\s*\(/.test(code) && !code.includes('{')) {
        maxControl = Math.max(maxControl, controlStack.filter(Boolean).length + 1);
      }
      if (started && depth === 0) { end = cursor; break; }
    }
    rows.push({ file: path.relative(root, file).replace(/\\/g, '/'), method: header[2], start: index + 1, end: end + 1,
                length: end - index + 1, nesting: maxControl });
  }
}
rows.sort((first, second) => second.length - first.length);
for (const row of rows) console.log([row.length, row.nesting, row.file, row.method, row.start + '-' + row.end].join('\t'));
