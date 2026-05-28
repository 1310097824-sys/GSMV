from __future__ import annotations

import argparse
import base64
import json
import math
import re
import statistics
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime
from html import escape
from io import BytesIO
from pathlib import Path
from typing import Any, Callable
from urllib import error, request


try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_REPORT_DIR = Path(r"D:\质量测试与保证实验\实验六\测试执行Excel报告")
PNG_1X1 = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
)


@dataclass
class HttpResult:
    status: int
    elapsed_ms: int
    body: Any
    text: str


def xml_text(value: Any) -> str:
    if value is None:
        return ""
    return escape(str(value), quote=False)


def column_name(index: int) -> str:
    name = ""
    while index:
        index, remainder = divmod(index - 1, 26)
        name = chr(65 + remainder) + name
    return name


def sheet_xml(rows: list[list[Any]]) -> str:
    col_count = max((len(row) for row in rows), default=1)
    cols = "".join(f'<col min="{i}" max="{i}" width="18" customWidth="1"/>' for i in range(1, col_count + 1))
    out = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">',
        f"<cols>{cols}</cols>",
        "<sheetData>",
    ]
    for r_idx, row in enumerate(rows, start=1):
        out.append(f'<row r="{r_idx}">')
        for c_idx, value in enumerate(row, start=1):
            ref = f"{column_name(c_idx)}{r_idx}"
            style = ' s="1"' if r_idx == 1 else ""
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                out.append(f'<c r="{ref}"{style}><v>{value}</v></c>')
            else:
                out.append(f'<c r="{ref}" t="inlineStr"{style}><is><t>{xml_text(value)}</t></is></c>')
        out.append("</row>")
    out.extend(["</sheetData>", "</worksheet>"])
    return "".join(out)


def write_xlsx(path: Path, sheets: dict[str, list[list[Any]]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet_names = list(sheets)
    content_types = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">',
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>',
        '<Default Extension="xml" ContentType="application/xml"/>',
        '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>',
        '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>',
    ]
    for i in range(1, len(sheet_names) + 1):
        content_types.append(f'<Override PartName="/xl/worksheets/sheet{i}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>')
    content_types.append("</Types>")

    workbook_sheets = "".join(
        f'<sheet name="{escape(name)}" sheetId="{i}" r:id="rId{i}"/>'
        for i, name in enumerate(sheet_names, start=1)
    )
    workbook = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
        f"<sheets>{workbook_sheets}</sheets></workbook>"
    )
    workbook_rels = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">',
    ]
    for i in range(1, len(sheet_names) + 1):
        workbook_rels.append(f'<Relationship Id="rId{i}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet{i}.xml"/>')
    workbook_rels.append(f'<Relationship Id="rId{len(sheet_names) + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>')
    workbook_rels.append("</Relationships>")
    styles = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        '<fonts count="2"><font><sz val="10"/><name val="Microsoft YaHei"/></font><font><b/><sz val="10"/><name val="Microsoft YaHei"/></font></fonts>'
        '<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD9EAF7"/></patternFill></fill></fills>'
        '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
        '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
        '<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>'
        '</styleSheet>'
    )
    root_rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
        '</Relationships>'
    )
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", "".join(content_types))
        zf.writestr("_rels/.rels", root_rels)
        zf.writestr("xl/workbook.xml", workbook)
        zf.writestr("xl/_rels/workbook.xml.rels", "".join(workbook_rels))
        zf.writestr("xl/styles.xml", styles)
        for i, name in enumerate(sheet_names, start=1):
            zf.writestr(f"xl/worksheets/sheet{i}.xml", sheet_xml(sheets[name]))


def parse_body(raw: bytes) -> tuple[Any, str]:
    text = raw.decode("utf-8", errors="replace")
    try:
        return json.loads(text), text
    except json.JSONDecodeError:
        return None, text


def http_json(base_url: str, method: str, path: str, payload: Any | None = None, token: str | None = None,
              timeout: int = 180) -> HttpResult:
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(base_url + path, data=data, headers=headers, method=method)
    start = time.perf_counter()
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            body, text = parse_body(resp.read())
            return HttpResult(resp.status, int((time.perf_counter() - start) * 1000), body, text)
    except error.HTTPError as exc:
        body, text = parse_body(exc.read())
        return HttpResult(exc.code, int((time.perf_counter() - start) * 1000), body, text)
    except Exception as exc:
        return HttpResult(0, int((time.perf_counter() - start) * 1000), None, str(exc))


def http_url(url: str, timeout: int = 60) -> HttpResult:
    req = request.Request(url, headers={"Accept": "text/html,application/json"})
    start = time.perf_counter()
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            body, text = parse_body(resp.read())
            return HttpResult(resp.status, int((time.perf_counter() - start) * 1000), body, text)
    except error.HTTPError as exc:
        body, text = parse_body(exc.read())
        return HttpResult(exc.code, int((time.perf_counter() - start) * 1000), body, text)
    except Exception as exc:
        return HttpResult(0, int((time.perf_counter() - start) * 1000), None, str(exc))


def multipart_body(fields: dict[str, Any], files: dict[str, tuple[str, str, bytes]]) -> tuple[str, bytes]:
    boundary = "----gsmv-exp6-" + uuid.uuid4().hex
    out = BytesIO()

    def line(value: bytes) -> None:
        out.write(value)
        out.write(b"\r\n")

    for name, value in fields.items():
        line(f"--{boundary}".encode())
        if isinstance(value, tuple):
            content_type, raw = value
            line(f'Content-Disposition: form-data; name="{name}"'.encode())
            line(f"Content-Type: {content_type}; charset=utf-8".encode())
            line(b"")
            out.write(json.dumps(raw, ensure_ascii=False).encode("utf-8") if isinstance(raw, (dict, list)) else str(raw).encode("utf-8"))
            out.write(b"\r\n")
        else:
            line(f'Content-Disposition: form-data; name="{name}"'.encode())
            line(b"")
            out.write(str(value).encode("utf-8"))
            out.write(b"\r\n")
    for name, (filename, content_type, content) in files.items():
        line(f"--{boundary}".encode())
        line(f'Content-Disposition: form-data; name="{name}"; filename="{filename}"'.encode())
        line(f"Content-Type: {content_type}".encode())
        line(b"")
        out.write(content)
        out.write(b"\r\n")
    line(f"--{boundary}--".encode())
    return f"multipart/form-data; boundary={boundary}", out.getvalue()


def http_multipart(base_url: str, path: str, fields: dict[str, Any], files: dict[str, tuple[str, str, bytes]],
                   token: str | None = None, timeout: int = 180) -> HttpResult:
    content_type, data = multipart_body(fields, files)
    headers = {"Accept": "application/json", "Content-Type": content_type}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(base_url + path, data=data, headers=headers, method="POST")
    start = time.perf_counter()
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            body, text = parse_body(resp.read())
            return HttpResult(resp.status, int((time.perf_counter() - start) * 1000), body, text)
    except error.HTTPError as exc:
        body, text = parse_body(exc.read())
        return HttpResult(exc.code, int((time.perf_counter() - start) * 1000), body, text)
    except Exception as exc:
        return HttpResult(0, int((time.perf_counter() - start) * 1000), None, str(exc))


def data_of(result: HttpResult) -> Any:
    return result.body.get("data") if isinstance(result.body, dict) else None


def message_of(result: HttpResult) -> str:
    if isinstance(result.body, dict):
        return str(result.body.get("message") or result.body.get("code") or "")
    return result.text[:80].replace("\n", " ")


def login(base_url: str) -> str:
    result = http_json(base_url, "POST", "/api/v1/auth/login", {"username": "admin", "password": "123456"})
    token = (data_of(result) or {}).get("accessToken")
    if result.status != 200 or not token:
        raise RuntimeError(f"登录失败：HTTP {result.status} {message_of(result)}")
    return token


def valid_image_bytes(path: str) -> bytes:
    image_path = Path(path)
    return image_path.read_bytes() if image_path.exists() else PNG_1X1


def status_text(ok: bool) -> str:
    return "通过" if ok else "未通过"


def percentile95(values: list[int]) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[max(0, min(len(ordered) - 1, math.ceil(len(ordered) * 0.95) - 1))]


WHITEBOX_META = {
    "utC01NullTextReturnsEmpty": ("UT-C-01", "RagTextChunker", "语句覆盖", "text=null", "返回空分块"),
    "utC02BlankTextReturnsEmpty": ("UT-C-02", "RagTextChunker", "判定覆盖", "空白文本", "返回空分块"),
    "utC03ShortTextCreatesSingleChunk": ("UT-C-03", "RagTextChunker", "基本路径", "短文本", "生成 1 个分块"),
    "utC04TextNormalizationRemovesExtraWhitespace": ("UT-C-04", "RagTextChunker", "条件覆盖", "多空格/多换行", "文本被规范化"),
    "utC05LongTextCreatesMultipleChunks": ("UT-C-05", "RagTextChunker", "循环覆盖", "1300 字符长文本", "生成多个分块"),
    "utC06LongChunksKeepOverlap": ("UT-C-06", "RagTextChunker", "边界路径", "长文本分块边界", "相邻分块有重叠"),
    "utC07SummaryIsTruncatedForLongContent": ("UT-C-07", "RagTextChunker", "边界值", "长摘要", "摘要截断并追加省略号"),
    "utV01CosineNullVectorReturnsZero": ("UT-V-01", "RagVectorUtils.cosine", "条件覆盖", "left=null", "返回 0"),
    "utV02CosineEmptyVectorReturnsZero": ("UT-V-02", "RagVectorUtils.cosine", "条件覆盖", "空向量", "返回 0"),
    "utV03CosineIdenticalVectorReturnsOne": ("UT-V-03", "RagVectorUtils.cosine", "路径覆盖", "相同向量", "返回 1"),
    "utV04CosineOrthogonalVectorReturnsZero": ("UT-V-04", "RagVectorUtils.cosine", "路径覆盖", "正交向量", "返回 0"),
    "utV05CosineTreatsNullElementAsZero": ("UT-V-05", "RagVectorUtils.cosine", "条件覆盖", "向量元素为 null", "按 0 处理"),
    "utV06CosineClampsNegativeResultToZero": ("UT-V-06", "RagVectorUtils.cosine", "边界覆盖", "负相关向量", "钳制为 0"),
    "utK01KeywordBlankQueryReturnsZero": ("UT-K-01", "RagVectorUtils.keywordScore", "判定覆盖", "query 为空", "返回 0"),
    "utK02KeywordBlankTextReturnsZero": ("UT-K-02", "RagVectorUtils.keywordScore", "判定覆盖", "text 为空", "返回 0"),
    "utK03KeywordFullPhraseReturnsOne": ("UT-K-03", "RagVectorUtils.keywordScore", "路径覆盖", "完整短语命中", "返回 1"),
    "utK04KeywordPartialTermsScoreFraction": ("UT-K-04", "RagVectorUtils.keywordScore", "路径覆盖", "部分词命中", "返回比例分"),
    "utK05KeywordPunctuationSplitMatchesTerms": ("UT-K-05", "RagVectorUtils.keywordScore", "条件覆盖", "标点分词", "词项可命中"),
    "utK06KeywordShortChineseFallbackMatches": ("UT-K-06", "RagVectorUtils.keywordScore", "边界覆盖", "短中文词", "fallback 分词命中"),
    "utQ01CacheMissingReturnsNull": ("UT-Q-01", "AssistantQueryCache", "路径覆盖", "未命中 key", "返回 null"),
    "utQ02CachePutThenGetHits": ("UT-Q-02", "AssistantQueryCache", "路径覆盖", "put 后 get", "缓存命中"),
    "utQ03CacheIgnoresNullKey": ("UT-Q-03", "AssistantQueryCache", "异常路径", "key=null", "忽略写入"),
    "utQ04CacheIgnoresNullResponse": ("UT-Q-04", "AssistantQueryCache", "异常路径", "response=null", "忽略写入"),
    "utQ05CacheInvalidateAllClearsEntries": ("UT-Q-05", "AssistantQueryCache", "路径覆盖", "invalidateAll", "缓存清空"),
    "utQ06CacheOverflowEvictsOldestEntry": ("UT-Q-06", "AssistantQueryCache", "边界覆盖", "257 条写入", "最旧条目被清理"),
    "utQ07ExpiredCacheEntryIsRemoved": ("UT-Q-07", "AssistantQueryCache", "边界覆盖", "过期条目", "get 时删除并返回 null"),
    "utG01GatewayParsesPlainJson": ("UT-G-01", "AiModelGateway JSON", "正常路径", "标准 JSON", "解析字段"),
    "utG02GatewayParsesMarkdownJsonFence": ("UT-G-02", "AiModelGateway JSON", "容错路径", "Markdown JSON fence", "剥离 fence 后解析"),
    "utG03GatewayParsesLenientJson": ("UT-G-03", "AiModelGateway JSON", "容错路径", "单引号/未引用字段", "宽松解析成功"),
    "utG04GatewayExtractsObjectFromArrayLikeText": ("UT-G-04", "AiModelGateway JSON", "容错路径", "数组样式文本", "提取对象块"),
    "utG05GatewayFallbackParsesStructuredText": ("UT-G-05", "AiModelGateway JSON", "兜底路径", "key=value 文本", "结构化兜底解析"),
    "utG06GatewayThrowsOnUnparseableText": ("UT-G-06", "AiModelGateway JSON", "异常路径", "不可解析文本", "抛出 BusinessException"),
    "utO01ObservationRuleTagsCompleteInput": ("UT-O-01", "ObservationAiService", "条件组合", "完整观测", "生成规则标签"),
    "utO02ObservationRuleTagsSummerDay": ("UT-O-02", "ObservationAiService", "条件组合", "夏季白天", "生成季节/昼夜标签"),
    "utO03ObservationRuleTagsWinterNight": ("UT-O-03", "ObservationAiService", "条件组合", "冬季夜间", "生成季节/昼夜标签"),
    "utO04ObservationRuleTagsEnvironmentExtremes": ("UT-O-04", "ObservationAiService", "条件组合", "高盐/高温/低氧/深水", "生成环境风险标签"),
    "utO05ObservationRuleTagsMultipleSpecies": ("UT-O-05", "ObservationAiService", "条件组合", "多物种", "生成共现标签"),
    "utO06ObservationEnvironmentEmptyTrue": ("UT-O-06", "ObservationAiService", "判定覆盖", "环境字段全空", "判定为空"),
    "utO07ObservationEnvironmentEmptyFalse": ("UT-O-07", "ObservationAiService", "判定覆盖", "水温非空", "判定非空"),
}


def run_whitebox(args: argparse.Namespace) -> Path:
    started = datetime.now()
    cmd = [str(ROOT / "mvnw.cmd"), "-Dtest=com.gsmv.ai.Experiment6WhiteboxExecutionTests", "test"]
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    report_path = ROOT / "target" / "surefire-reports" / "TEST-com.gsmv.ai.Experiment6WhiteboxExecutionTests.xml"
    rows = [["用例编号", "测试对象", "覆盖方法", "输入/操作", "预期结果", "实际结果", "耗时ms", "结论"]]
    total = passed = failed = errors = skipped = 0
    if report_path.exists():
        tree = ET.parse(report_path)
        suite = tree.getroot()
        total = int(suite.attrib.get("tests", 0))
        failed = int(suite.attrib.get("failures", 0))
        errors = int(suite.attrib.get("errors", 0))
        skipped = int(suite.attrib.get("skipped", 0))
        for case in suite.findall("testcase"):
            name = case.attrib["name"]
            meta = WHITEBOX_META.get(name, (name, "未知对象", "未登记", "", ""))
            has_failure = case.find("failure") is not None or case.find("error") is not None
            is_skipped = case.find("skipped") is not None
            result = "跳过" if is_skipped else ("未通过" if has_failure else "通过")
            actual = "JUnit 断言通过" if result == "通过" else "JUnit 断言失败或异常"
            rows.append([*meta, actual, round(float(case.attrib.get("time", 0)) * 1000, 2), result])
    passed = total - failed - errors - skipped
    summary = [
        ["项目", "值"],
        ["执行脚本", "run_whitebox_excel.ps1"],
        ["执行命令", " ".join(cmd)],
        ["开始时间", started.strftime("%Y-%m-%d %H:%M:%S")],
        ["结束时间", datetime.now().strftime("%Y-%m-%d %H:%M:%S")],
        ["总用例数", total],
        ["通过数", passed],
        ["失败数", failed],
        ["错误数", errors],
        ["跳过数", skipped],
        ["Maven 退出码", proc.returncode],
    ]
    output = Path(args.output_dir) / "01_白盒单元测试执行报告.xlsx"
    write_xlsx(output, {"Summary": summary, "WhiteboxCases": rows, "MavenOutput": [["stdout"], [proc.stdout[-30000:]], ["stderr"], [proc.stderr[-10000:]]]})
    if proc.returncode != 0:
        raise SystemExit(f"白盒测试未全部通过，Excel 已生成：{output}")
    return output


def run_functional(args: argparse.Namespace) -> Path:
    token = login(args.base_url)
    image = valid_image_bytes(args.image)
    results: dict[str, tuple[bool, str, int]] = {}

    def record(key: str, result: HttpResult, ok: bool, detail: str) -> None:
        results[key] = (ok, f"HTTP {result.status} / {result.elapsed_ms}ms，{detail}", result.elapsed_ms)

    assistant_payload = {"message": "近30天湛江近海有哪些高风险观测？", "history": []}
    r = http_json(args.base_url, "POST", "/api/v1/ai/assistant/chat", assistant_payload, token)
    d = data_of(r) or {}
    record("assistant_valid", r, r.status == 200 and bool(d.get("answer")), f"answerLen={len(d.get('answer') or '')}，evidence={len(d.get('evidence') or [])}，cacheHit={d.get('cacheHit')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/assistant/chat", {"message": "", "history": []}, token)
    record("assistant_empty", r, r.status == 400, f"message={message_of(r)[:40]}")
    r = http_multipart(args.base_url, "/api/v1/ai/species/identify", {}, {"file": ("OIP.jpg", "image/jpeg", image)}, token)
    d = data_of(r) or {}
    record("identify_valid", r, r.status == 200, f"likely={d.get('likelyChineseName')}，confidence={d.get('confidence')}，needsHumanReview={d.get('needsHumanReview')}")
    r = http_multipart(args.base_url, "/api/v1/ai/species/identify", {}, {"file": ("empty.png", "image/png", b"")}, token)
    record("identify_empty", r, r.status == 400, f"message={message_of(r)[:40]}")
    r = http_multipart(args.base_url, "/api/v1/ai/species/identify", {}, {"file": ("bad.txt", "text/plain", b"not image")}, token)
    record("identify_nonimage", r, r.status == 400, f"message={message_of(r)[:40]}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/autocomplete", {"chineseName": "中华白海豚"}, token)
    d = data_of(r) or {}
    record("autocomplete_valid", r, r.status == 200 and bool(d.get("chineseName")), f"confidence={d.get('confidence')}，summaryLen={len(d.get('summary') or '')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/autocomplete", {"chineseName": "", "scientificName": ""}, token)
    record("autocomplete_empty", r, r.status == 400, f"message={message_of(r)[:40]}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/polish", {"fieldName": "description", "text": "常见于近岸水域，群体活动。"}, token)
    d = data_of(r) or {}
    record("polish_valid", r, r.status == 200 and bool(d.get("polishedText")), f"polishedLen={len(d.get('polishedText') or '')}，keywords={len(d.get('keywords') or [])}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/polish", {"fieldName": "description", "text": ""}, token)
    record("polish_empty", r, r.status == 400, f"message={message_of(r)[:40]}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/translate", {"chineseName": "中华白海豚", "description": "近岸水域。", "targetLanguage": "English"}, token)
    d = data_of(r) or {}
    record("translate_valid", r, r.status == 200 and d.get("targetLanguage") == "English", f"summaryLen={len(d.get('summary') or '')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/translate", {"chineseName": "中华白海豚", "description": "近岸水域。", "targetLanguage": ""}, token)
    record("translate_empty", r, r.status == 400, f"message={message_of(r)[:40]}")
    observation = {
        "ecosystemId": 1, "ecosystemName": "湛江近海", "observedAt": datetime.now().replace(microsecond=0).isoformat(),
        "locationLat": 21.18, "locationLng": 110.53, "locationName": "湛江东里海草床",
        "note": "发现中华白海豚活动，需关注船舶干扰。",
        "environment": {"waterTemperature": 25.8, "salinity": 31.6, "ph": 8.1, "dissolvedOxygen": 6.4},
        "speciesItems": [{"scientificName": "Sousa chinensis", "chineseName": "中华白海豚", "countEstimated": 3, "behavior": "觅食"}],
    }
    r = http_json(args.base_url, "POST", "/api/v1/ai/observations/analyze", observation, token)
    d = data_of(r) or {}
    record("observation_valid", r, r.status == 200 and bool(d.get("summary")), f"summaryLen={len(d.get('summary') or '')}，tags={len(d.get('tags') or [])}")
    bad_observation = dict(observation)
    bad_observation.pop("locationLat")
    r = http_json(args.base_url, "POST", "/api/v1/ai/observations/analyze", bad_observation, token)
    record("observation_missing", r, r.status == 400, f"message={message_of(r)[:40]}")
    for key, limit in [("rag_limit_1", 1), ("rag_default", None), ("rag_limit_50", 50)]:
        payload = {"query": "中华白海豚 湛江近海"}
        if limit is not None:
            payload["limit"] = limit
        r = http_json(args.base_url, "POST", "/api/v1/ai/rag/search-test", payload, token)
        d = data_of(r) or []
        record(key, r, r.status == 200 and isinstance(d, list), f"hits={len(d)}，limit={limit or '默认'}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/rag/search-test", {"query": "", "limit": 5}, token)
    record("rag_empty", r, r.status == 400, f"message={message_of(r)[:40]}")
    for days in [1, 30, 365]:
        r = http_json(args.base_url, "POST", "/api/v1/ai/reports/generate", {"days": days}, token)
        d = data_of(r) or {}
        record(f"report_{days}", r, r.status == 200 and d.get("days") == days, f"reportId={d.get('id')}，type={d.get('reportType')}")
    for days in [0, 366]:
        r = http_json(args.base_url, "POST", "/api/v1/ai/reports/generate", {"days": days}, token)
        record(f"report_{days}", r, r.status == 400, f"message={message_of(r)[:40]}")
    review_payload = {"likelyChineseName": "中华白海豚", "likelyScientificName": "Sousa chinensis", "confidence": 0.62, "needsHumanReview": True, "reasoning": "功能测试", "candidates": [], "relatedSpeciesRecords": [], "ragEvidence": [], "ragConclusion": "功能测试", "conflictWarnings": [], "submitNote": "功能测试"}
    r = http_multipart(args.base_url, "/api/v1/ai/review-tickets", {"payload": ("application/json", review_payload)}, {"file": ("review.png", "image/png", PNG_1X1)}, token)
    d = data_of(r) or {}
    ticket_id = d.get("id")
    record("review_create", r, r.status == 200 and bool(ticket_id), f"ticketId={ticket_id}，status={d.get('status')}")
    r = http_multipart(args.base_url, "/api/v1/ai/review-tickets", {"payload": ("application/json", review_payload)}, {"file": ("bad.txt", "text/plain", b"bad")}, token)
    record("review_nonimage", r, r.status == 400, f"message={message_of(r)[:40]}")
    if ticket_id:
        r = http_json(args.base_url, "POST", f"/api/v1/ai/review-tickets/{ticket_id}/start-review", None, token)
        d = data_of(r) or {}
        record("review_start", r, r.status == 200 and d.get("status") == "IN_REVIEW", f"status={d.get('status')}")
        r = http_json(args.base_url, "POST", f"/api/v1/ai/review-tickets/{ticket_id}/resolve", {"resolutionCode": "UNABLE_TO_CONFIRM", "reviewNote": "功能测试复核结论"}, token)
        d = data_of(r) or {}
        record("review_resolve", r, r.status == 200 and d.get("status") == "RESOLVED", f"status={d.get('status')}，resolution={d.get('resolutionCode')}")
    r = http_json(args.base_url, "GET", "/api/v1/ai/review-tickets?page=1&size=5", token=token)
    d = data_of(r) or {}
    record("review_list", r, r.status == 200 and isinstance(d.get("items"), list), f"total={d.get('total')}，items={len(d.get('items') or [])}")

    def row(case_id: str, category: str, obj: str, method: str, key: str, expected: str) -> list[Any]:
        ok, actual, elapsed = results[key]
        return [case_id, category, obj, method, expected, actual, elapsed, status_text(ok)]

    rows = [["用例编号", "类别", "测试对象", "测试方法", "预期结果", "实际结果", "耗时ms", "结论"]]
    base_rows = [
        ("EC-01", "等价类", "AI助手有效问答", "有效等价类", "assistant_valid", "返回答案和证据"),
        ("EC-02", "等价类", "AI助手空问题", "无效等价类", "assistant_empty", "HTTP 400"),
        ("EC-03", "等价类", "有效图片识图", "有效等价类", "identify_valid", "返回识图结果"),
        ("EC-04", "等价类", "空文件识图", "无效等价类", "identify_empty", "HTTP 400"),
        ("EC-05", "等价类", "非图片识图", "无效等价类", "identify_nonimage", "HTTP 400"),
        ("EC-06", "等价类", "物种补全有效输入", "有效等价类", "autocomplete_valid", "返回补全字段"),
        ("EC-07", "等价类", "物种补全空输入", "无效等价类", "autocomplete_empty", "HTTP 400"),
        ("EC-08", "等价类", "文本润色有效输入", "有效等价类", "polish_valid", "返回润色文本"),
        ("EC-09", "等价类", "文本润色空文本", "无效等价类", "polish_empty", "HTTP 400"),
        ("EC-10", "等价类", "翻译有效输入", "有效等价类", "translate_valid", "返回翻译摘要"),
        ("EC-11", "等价类", "翻译空目标语言", "无效等价类", "translate_empty", "HTTP 400"),
        ("EC-12", "等价类", "观测分析完整输入", "有效等价类", "observation_valid", "返回摘要标签"),
        ("EC-13", "等价类", "观测缺纬度", "无效等价类", "observation_missing", "HTTP 400"),
        ("EC-14", "等价类", "RAG有效查询", "有效等价类", "rag_limit_1", "返回证据"),
        ("EC-15", "等价类", "RAG空查询", "无效等价类", "rag_empty", "HTTP 400"),
        ("EC-16", "等价类", "报告days=30", "有效等价类", "report_30", "生成报告"),
        ("EC-17", "等价类", "报告days=366", "无效等价类", "report_366", "HTTP 400"),
        ("EC-18", "等价类", "复核工单图片", "有效等价类", "review_create", "创建工单"),
        ("EC-19", "等价类", "复核工单非图片", "无效等价类", "review_nonimage", "HTTP 400"),
        ("EC-20", "等价类", "复核工单列表", "有效等价类", "review_list", "返回分页"),
        ("BV-01", "边界值", "报告days=1", "边界值", "report_1", "生成报告"),
        ("BV-02", "边界值", "报告days=30", "边界值", "report_30", "生成报告"),
        ("BV-03", "边界值", "报告days=365", "边界值", "report_365", "生成报告"),
        ("BV-04", "边界值", "报告days=0", "边界值", "report_0", "HTTP 400"),
        ("BV-05", "边界值", "报告days=366", "边界值", "report_366", "HTTP 400"),
        ("BV-06", "边界值", "文件0KB", "边界值", "identify_empty", "HTTP 400"),
        ("BV-07", "边界值", "正常图片", "边界值", "identify_valid", "识图成功"),
        ("BV-08", "边界值", "RAG limit=1", "边界值", "rag_limit_1", "最多1条"),
        ("BV-09", "边界值", "RAG默认limit", "边界值", "rag_default", "默认返回"),
        ("BV-10", "边界值", "RAG limit=50", "边界值", "rag_limit_50", "不超过上限"),
        ("BV-11", "边界值", "助手缓存复测", "边界值", "assistant_valid", "可稳定返回"),
        ("BV-12", "边界值", "复核状态流转", "边界值", "review_resolve", "RESOLVED"),
        ("CG-01", "因果图", "非图片附件", "决策表R1", "review_nonimage", "拒绝创建"),
        ("CG-02", "因果图", "低置信度图片", "决策表R3", "review_create", "允许创建"),
        ("CG-03", "因果图", "开始复核", "状态迁移", "review_start", "IN_REVIEW"),
        ("CG-04", "因果图", "提交结论", "状态迁移", "review_resolve", "RESOLVED"),
        ("CG-05", "因果图", "复核列表", "状态查询", "review_list", "可查询"),
        ("CG-06", "因果图", "识图证据", "复核触发", "identify_valid", "返回复核标记"),
        ("FT-01", "系统功能", "助手问答", "端到端", "assistant_valid", "成功"),
        ("FT-02", "系统功能", "识图", "端到端", "identify_valid", "成功"),
        ("FT-03", "系统功能", "补全", "端到端", "autocomplete_valid", "成功"),
        ("FT-04", "系统功能", "润色", "端到端", "polish_valid", "成功"),
        ("FT-05", "系统功能", "翻译", "端到端", "translate_valid", "成功"),
        ("FT-06", "系统功能", "观测分析", "端到端", "observation_valid", "成功"),
        ("FT-07", "系统功能", "RAG检索", "端到端", "rag_limit_50", "成功"),
        ("FT-08", "系统功能", "报告生成", "端到端", "report_30", "成功"),
        ("FT-09", "系统功能", "复核创建", "端到端", "review_create", "成功"),
        ("FT-10", "系统功能", "复核开始", "端到端", "review_start", "成功"),
        ("FT-11", "系统功能", "复核结论", "端到端", "review_resolve", "成功"),
        ("FT-12", "系统功能", "异常校验", "端到端", "rag_empty", "成功拦截"),
        ("FT-13", "系统功能", "列表查询", "端到端", "review_list", "成功"),
    ]
    for item in base_rows:
        rows.append(row(*item))
    passed = sum(1 for r in rows[1:] if r[-1] == "通过")
    summary = [["项目", "值"], ["执行脚本", "run_functional_excel.ps1"], ["用例数", len(rows) - 1], ["通过数", passed], ["未通过数", len(rows) - 1 - passed], ["说明", "脚本真实调用后端接口并生成 Excel"]]
    output = Path(args.output_dir) / "02_功能测试执行报告.xlsx"
    write_xlsx(output, {"Summary": summary, "FunctionalCases": rows})
    return output


def run_performance(args: argparse.Namespace) -> Path:
    token = login(args.base_url)
    image = valid_image_bytes(args.image)

    def sample(code: str, obj: str, action: str, samples: int, threshold: int, fn: Callable[[int], tuple[bool, str]]) -> list[Any]:
        durations: list[int] = []
        successes = 0
        detail = ""
        for i in range(samples):
            start = time.perf_counter()
            ok, detail = fn(i)
            durations.append(int((time.perf_counter() - start) * 1000))
            successes += 1 if ok else 0
        avg = round(statistics.mean(durations), 2) if durations else 0
        p95 = percentile95(durations)
        ok = successes == samples and p95 <= threshold
        return [code, obj, action, samples, avg, min(durations), max(durations), p95, f"{successes / samples * 100:.0f}%", detail, f"P95<={threshold}ms", status_text(ok)]

    def parallel_sample(code: str, obj: str, action: str, samples: int, workers: int, threshold: int, fn: Callable[[int], tuple[bool, str]]) -> list[Any]:
        durations: list[int] = []
        successes = 0
        detail = ""
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = []
            for i in range(samples):
                futures.append(pool.submit(lambda idx=i: timed(fn, idx)))
            for future in as_completed(futures):
                duration, ok, detail = future.result()
                durations.append(duration)
                successes += 1 if ok else 0
        avg = round(statistics.mean(durations), 2) if durations else 0
        p95 = percentile95(durations)
        ok = successes == samples and p95 <= threshold
        return [code, obj, action, f"{samples}次/{workers}并发", avg, min(durations), max(durations), p95, f"{successes / samples * 100:.0f}%", detail, f"P95<={threshold}ms", status_text(ok)]

    def timed(fn: Callable[[int], tuple[bool, str]], i: int) -> tuple[int, bool, str]:
        start = time.perf_counter()
        ok, detail = fn(i)
        return int((time.perf_counter() - start) * 1000), ok, detail

    def j(method: str, path: str, payload: Any | None = None, t: str | None = token) -> HttpResult:
        return http_json(args.base_url, method, path, payload, t)

    rows = [["编号", "测试对象", "接口/动作", "样本/负载", "平均ms", "最小ms", "最大ms", "P95ms", "成功率", "关键返回", "通过准则", "结论"]]
    rows.append(sample("PT-01", "后端健康检查", "GET /actuator/health", 10, 500, lambda i: ((r := j("GET", "/actuator/health", t=None)).status == 200, f"status={(r.body or {}).get('status') if isinstance(r.body, dict) else None}")))
    rows.append(sample("PT-02", "前端登录页", "GET /login", 5, 1000, lambda i: ((r := http_url(args.frontend_url + "/login")).status == 200, f"bytes={len(r.text)}")))
    rows.append(sample("PT-03", "登录接口", "POST /api/v1/auth/login", 5, 1500, lambda i: ((r := http_json(args.base_url, "POST", "/api/v1/auth/login", {"username": "admin", "password": "123456"})).status == 200, f"HTTP {r.status}")))
    rows.append(sample("PT-04", "Qdrant状态", "GET /api/v1/ai/rag/qdrant/status", 5, 2000, lambda i: ((r := j("GET", "/api/v1/ai/rag/qdrant/status")).status == 200, f"points={(data_of(r) or {}).get('pointsCount')}")))
    rows.append(sample("PT-05", "RAG检索limit=1", "POST /api/v1/ai/rag/search-test", 5, 3000, lambda i: ((r := j("POST", "/api/v1/ai/rag/search-test", {"query": "中华白海豚 湛江近海", "limit": 1})).status == 200, f"hits={len(data_of(r) or [])}")))
    rows.append(sample("PT-06", "RAG检索limit=5", "POST /api/v1/ai/rag/search-test", 5, 3000, lambda i: ((r := j("POST", "/api/v1/ai/rag/search-test", {"query": "中华白海豚 湛江近海", "limit": 5})).status == 200, f"hits={len(data_of(r) or [])}")))
    rows.append(parallel_sample("PT-07", "RAG并发检索", "POST /api/v1/ai/rag/search-test", 5, 5, 5000, lambda i: ((r := j("POST", "/api/v1/ai/rag/search-test", {"query": "中华白海豚 湛江近海", "limit": 5})).status == 200, f"hits={len(data_of(r) or [])}")))
    rows.append(sample("PT-08", "AI助手首次问答", "POST /api/v1/ai/assistant/chat", 3, 20000, lambda i: ((r := j("POST", "/api/v1/ai/assistant/chat", {"message": f"性能测试{i} 湛江近海风险", "history": []})).status == 200, f"cacheHit={(data_of(r) or {}).get('cacheHit')}")))
    cache_question = "性能测试缓存问题：湛江近海风险概述"
    j("POST", "/api/v1/ai/assistant/chat", {"message": cache_question, "history": []})
    rows.append(sample("PT-09", "AI助手缓存命中", "POST /api/v1/ai/assistant/chat", 5, 500, lambda i: ((r := j("POST", "/api/v1/ai/assistant/chat", {"message": cache_question, "history": []})).status == 200 and (data_of(r) or {}).get("cacheHit") is True, f"cacheHit={(data_of(r) or {}).get('cacheHit')}")))
    rows.append(parallel_sample("PT-10", "AI助手缓存并发", "POST /api/v1/ai/assistant/chat", 5, 5, 1000, lambda i: ((r := j("POST", "/api/v1/ai/assistant/chat", {"message": cache_question, "history": []})).status == 200, f"cacheHit={(data_of(r) or {}).get('cacheHit')}")))
    rows.append(sample("PT-11", "百炼视觉识图", "POST /api/v1/ai/species/identify", 3, 20000, lambda i: ((r := http_multipart(args.base_url, "/api/v1/ai/species/identify", {}, {"file": ("OIP.jpg", "image/jpeg", image)}, token)).status == 200, f"confidence={(data_of(r) or {}).get('confidence')}")))
    rows.append(sample("PT-12", "DeepSeek物种补全", "POST /api/v1/ai/species/autocomplete", 3, 15000, lambda i: ((r := j("POST", "/api/v1/ai/species/autocomplete", {"chineseName": "中华白海豚"})).status == 200, f"confidence={(data_of(r) or {}).get('confidence')}")))
    rows.append(sample("PT-13", "DeepSeek文本润色", "POST /api/v1/ai/species/polish", 3, 12000, lambda i: ((r := j("POST", "/api/v1/ai/species/polish", {"fieldName": "description", "text": "常见于近岸水域，群体活动。"})).status == 200, f"keywords={len((data_of(r) or {}).get('keywords') or [])}")))
    rows.append(sample("PT-14", "DeepSeek物种翻译", "POST /api/v1/ai/species/translate", 3, 12000, lambda i: ((r := j("POST", "/api/v1/ai/species/translate", {"chineseName": "中华白海豚", "description": "近岸水域。", "targetLanguage": "English"})).status == 200, f"summaryLen={len((data_of(r) or {}).get('summary') or '')}")))
    observation = {"ecosystemId": 1, "ecosystemName": "湛江近海", "observedAt": datetime.now().replace(microsecond=0).isoformat(), "locationLat": 21.18, "locationLng": 110.53, "environment": {"waterTemperature": 25.8, "salinity": 31.6, "ph": 8.1, "dissolvedOxygen": 6.4}, "speciesItems": [{"scientificName": "Sousa chinensis", "chineseName": "中华白海豚", "countEstimated": 3}]}
    rows.append(sample("PT-15", "DeepSeek观测分析", "POST /api/v1/ai/observations/analyze", 3, 15000, lambda i: ((r := j("POST", "/api/v1/ai/observations/analyze", observation)).status == 200, f"tags={len((data_of(r) or {}).get('tags') or [])}")))
    for days in [1, 30, 365]:
        rows.append(sample(f"PT-{15 + [1, 30, 365].index(days) + 1:02d}", f"AI科研报告days={days}", "POST /api/v1/ai/reports/generate", 1, 25000, lambda i, d=days: ((r := j("POST", "/api/v1/ai/reports/generate", {"days": d})).status == 200, f"reportId={(data_of(r) or {}).get('id')}")))
    rows.append(sample("PT-19", "报告列表", "GET /api/v1/ai/reports", 5, 1500, lambda i: ((r := j("GET", "/api/v1/ai/reports?page=1&size=10")).status == 200, f"items={len((data_of(r) or {}).get('items') or [])}")))
    rows.append(sample("PT-20", "复核工单列表", "GET /api/v1/ai/review-tickets", 10, 1500, lambda i: ((r := j("GET", "/api/v1/ai/review-tickets?page=1&size=10")).status == 200, f"items={len((data_of(r) or {}).get('items') or [])}")))
    review_payload = {"likelyChineseName": "中华白海豚", "confidence": 0.62, "needsHumanReview": True, "reasoning": "性能测试", "candidates": [], "relatedSpeciesRecords": [], "ragEvidence": [], "ragConclusion": "性能测试", "conflictWarnings": [], "submitNote": "性能测试"}
    rows.append(sample("PT-21", "复核工单创建", "POST /api/v1/ai/review-tickets", 3, 5000, lambda i: ((r := http_multipart(args.base_url, "/api/v1/ai/review-tickets", {"payload": ("application/json", review_payload)}, {"file": ("perf.png", "image/png", PNG_1X1)}, token)).status == 200, f"ticketId={(data_of(r) or {}).get('id')}")))
    rows.append(sample("PT-22", "RAG较大limit", "POST /api/v1/ai/rag/search-test limit=50", 3, 5000, lambda i: ((r := j("POST", "/api/v1/ai/rag/search-test", {"query": "中华白海豚 湛江近海", "limit": 50})).status == 200, f"hits={len(data_of(r) or [])}")))
    passed = sum(1 for row in rows[1:] if row[-1] == "通过")
    summary = [["项目", "值"], ["执行脚本", "run_performance_excel.ps1"], ["场景数", len(rows) - 1], ["通过数", passed], ["未通过数", len(rows) - 1 - passed], ["说明", "真实系统多轮采样，记录平均、最小、最大、P95、成功率"]]
    output = Path(args.output_dir) / "03_性能测试执行报告.xlsx"
    write_xlsx(output, {"Summary": summary, "PerformanceCases": rows})
    return output


def run_integration(args: argparse.Namespace) -> Path:
    token = login(args.base_url)
    image = valid_image_bytes(args.image)
    rows = [["编号", "联调阶段", "上游模块", "下游模块", "接口/动作", "状态/耗时", "实际结果", "结论"]]

    def add(code: str, phase: str, upstream: str, downstream: str, action: str, result: HttpResult, ok: bool, actual: str) -> None:
        rows.append([code, phase, upstream, downstream, action, f"HTTP {result.status} / {result.elapsed_ms}ms", actual, status_text(ok)])

    r = http_json(args.base_url, "GET", "/actuator/health")
    add("RT-01", "环境认证", "Spring Boot", "Actuator", "GET /actuator/health", r, r.status == 200, f"status={(r.body or {}).get('status') if isinstance(r.body, dict) else None}")
    r = http_url(args.frontend_url + "/login")
    add("RT-02", "环境认证", "前端", "登录页", "GET /login", r, r.status == 200, f"bytes={len(r.text)}")
    r = http_json(args.base_url, "POST", "/api/v1/auth/login", {"username": "admin", "password": "123456"})
    d = data_of(r) or {}
    add("RT-03", "环境认证", "Auth", "JWT", "POST /auth/login", r, r.status == 200, f"authorities={len((d.get('user') or {}).get('authorities') or [])}")
    r = http_json(args.base_url, "POST", "/api/v1/auth/login", {"username": "admin", "password": "wrong"})
    add("RT-04", "环境认证", "Auth", "安全拦截", "POST /auth/login wrong", r, r.status == 401, message_of(r))
    r = http_json(args.base_url, "GET", "/api/v1/ai/rag/documents")
    add("RT-05", "环境认证", "Security", "RAG", "GET /rag/documents no token", r, r.status == 401, message_of(r))
    r = http_json(args.base_url, "GET", "/api/v1/ai/rag/qdrant/status", token=token)
    d = data_of(r) or {}
    add("RT-06", "RAG", "RAG服务", "Qdrant", "GET /rag/qdrant/status", r, r.status == 200, f"available={d.get('available')}，points={d.get('pointsCount')}")
    r = http_json(args.base_url, "GET", "/api/v1/ai/rag/sources", token=token)
    add("RT-07", "RAG", "来源配置", "RAG页面", "GET /rag/sources", r, r.status == 200, f"sources={len(data_of(r) or [])}")
    rag_text = f"联调资料 {uuid.uuid4().hex}: 中华白海豚 Sousa chinensis 湛江近海 RAG 证据。"
    r = http_multipart(args.base_url, "/api/v1/ai/rag/documents/upload", {}, {"file": ("integration-rag.txt", "text/plain", rag_text.encode("utf-8"))}, token)
    d = data_of(r) or {}
    document_id = (d.get("document") or {}).get("id")
    add("RT-08", "RAG", "文件上传", "rag_document/rag_chunk", "POST /rag/documents/upload", r, r.status == 200 and bool(document_id), f"documentId={document_id}，chunks={len(d.get('chunks') or [])}")
    r = http_json(args.base_url, "GET", "/api/v1/ai/rag/documents?page=1&size=5", token=token)
    add("RT-09", "RAG", "rag_document", "列表接口", "GET /rag/documents", r, r.status == 200, f"items={len((data_of(r) or {}).get('items') or [])}")
    if document_id:
        r = http_json(args.base_url, "GET", f"/api/v1/ai/rag/documents/{document_id}", token=token)
        add("RT-10", "RAG", "rag_document", "详情接口", "GET /rag/documents/{id}", r, r.status == 200, f"chunks={len((data_of(r) or {}).get('chunks') or [])}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/rag/qdrant/rebuild", token=token)
    d = data_of(r) or {}
    add("RT-11", "RAG", "rag_chunk", "Qdrant", "POST /rag/qdrant/rebuild", r, r.status == 200, f"points={d.get('pointsCount')}，readyChunks={d.get('readyChunks')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/rag/search-test", {"query": "中华白海豚 湛江近海 联调资料", "limit": 5}, token)
    add("RT-12", "RAG", "Qdrant", "AI证据", "POST /rag/search-test", r, r.status == 200, f"hits={len(data_of(r) or [])}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/rag/search-test", {"query": "", "limit": 5}, token)
    add("RT-13", "RAG", "请求校验", "RAG检索", "POST /rag/search-test empty", r, r.status == 400, message_of(r))
    q = "联调助手问题 " + uuid.uuid4().hex[:6]
    r = http_json(args.base_url, "POST", "/api/v1/ai/assistant/chat", {"message": q, "history": []}, token)
    d = data_of(r) or {}
    add("RT-14", "AI助手", "Assistant", "DeepSeek/RAG", "POST /assistant/chat", r, r.status == 200, f"answerLen={len(d.get('answer') or '')}，evidence={len(d.get('evidence') or [])}，cacheHit={d.get('cacheHit')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/assistant/chat", {"message": q, "history": []}, token)
    add("RT-15", "AI助手", "缓存", "助手接口", "POST /assistant/chat same", r, r.status == 200 and (data_of(r) or {}).get("cacheHit") is True, f"cacheHit={(data_of(r) or {}).get('cacheHit')}")
    r = http_multipart(args.base_url, "/api/v1/ai/species/identify", {}, {"file": ("OIP.jpg", "image/jpeg", image)}, token)
    identify = data_of(r) or {}
    add("RT-16", "识图", "百炼视觉", "RAG/复核", "POST /species/identify", r, r.status == 200, f"likely={identify.get('likelyChineseName')}，confidence={identify.get('confidence')}，evidence={len(identify.get('ragEvidence') or [])}")
    r = http_multipart(args.base_url, "/api/v1/ai/species/identify", {}, {"file": ("bad.txt", "text/plain", b"bad")}, token)
    add("RT-17", "识图", "文件校验", "识图服务", "POST /species/identify nonimage", r, r.status == 400, message_of(r))
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/autocomplete", {"chineseName": "中华白海豚"}, token)
    add("RT-18", "物种AI", "物种表单", "DeepSeek", "POST /species/autocomplete", r, r.status == 200, f"confidence={(data_of(r) or {}).get('confidence')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/polish", {"fieldName": "description", "text": "常见于近岸水域。"}, token)
    add("RT-19", "物种AI", "表单文本", "DeepSeek", "POST /species/polish", r, r.status == 200, f"polishedLen={len((data_of(r) or {}).get('polishedText') or '')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/species/translate", {"chineseName": "中华白海豚", "description": "近岸水域。", "targetLanguage": "English"}, token)
    add("RT-20", "物种AI", "物种字段", "DeepSeek", "POST /species/translate", r, r.status == 200, f"target={(data_of(r) or {}).get('targetLanguage')}")
    observation = {"ecosystemId": 1, "ecosystemName": "湛江近海", "observedAt": datetime.now().replace(microsecond=0).isoformat(), "locationLat": 21.18, "locationLng": 110.53, "environment": {"waterTemperature": 25.8, "salinity": 31.6, "ph": 8.1, "dissolvedOxygen": 6.4}, "speciesItems": [{"scientificName": "Sousa chinensis", "chineseName": "中华白海豚", "countEstimated": 3}]}
    r = http_json(args.base_url, "POST", "/api/v1/ai/observations/analyze", observation, token)
    add("RT-21", "观测AI", "观测表单", "DeepSeek", "POST /observations/analyze", r, r.status == 200, f"tags={len((data_of(r) or {}).get('tags') or [])}")
    bad_observation = dict(observation)
    bad_observation.pop("locationLat")
    r = http_json(args.base_url, "POST", "/api/v1/ai/observations/analyze", bad_observation, token)
    add("RT-22", "观测AI", "请求校验", "观测分析", "POST /observations/analyze bad", r, r.status == 400, message_of(r))
    r = http_json(args.base_url, "POST", "/api/v1/ai/reports/generate", {"days": 30}, token)
    report = data_of(r) or {}
    report_id = report.get("id")
    add("RT-23", "科研报告", "统计/RAG/DeepSeek", "ai_research_report", "POST /reports/generate", r, r.status == 200, f"reportId={report_id}，highlights={len(report.get('highlights') or [])}")
    if report_id:
        r = http_json(args.base_url, "GET", f"/api/v1/ai/reports/{report_id}", token=token)
        add("RT-24", "科研报告", "报告表", "详情页", "GET /reports/{id}", r, r.status == 200, f"titleLen={len((data_of(r) or {}).get('title') or '')}")
    r = http_json(args.base_url, "GET", "/api/v1/ai/reports?page=1&size=10", token=token)
    add("RT-25", "科研报告", "报告表", "列表页", "GET /reports", r, r.status == 200, f"items={len((data_of(r) or {}).get('items') or [])}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/reports/generate", {"days": 366}, token)
    add("RT-26", "科研报告", "请求校验", "报告生成", "POST /reports/generate bad", r, r.status == 400, message_of(r))
    review_payload = {"likelyChineseName": identify.get("likelyChineseName") or "中华白海豚", "likelyScientificName": identify.get("likelyScientificName") or "Sousa chinensis", "confidence": identify.get("confidence") or 0.62, "needsHumanReview": bool(identify.get("needsHumanReview")), "reasoning": identify.get("reasoning") or "联调测试", "candidates": identify.get("candidates") or [], "relatedSpeciesRecords": identify.get("relatedSpeciesRecords") or [], "ragEvidence": identify.get("ragEvidence") or [], "ragConclusion": identify.get("ragConclusion") or "联调测试", "conflictWarnings": identify.get("conflictWarnings") or [], "submitNote": "联调测试"}
    r = http_multipart(args.base_url, "/api/v1/ai/review-tickets", {"payload": ("application/json", review_payload)}, {"file": ("OIP.jpg", "image/jpeg", image)}, token)
    ticket = data_of(r) or {}
    ticket_id = ticket.get("id")
    add("RT-27", "复核闭环", "识图结果", "复核工单", "POST /review-tickets", r, r.status == 200, f"ticketId={ticket_id}，status={ticket.get('status')}")
    r = http_json(args.base_url, "GET", "/api/v1/ai/review-tickets?page=1&size=10", token=token)
    add("RT-28", "复核闭环", "工单表", "列表页", "GET /review-tickets", r, r.status == 200, f"items={len((data_of(r) or {}).get('items') or [])}")
    if ticket_id:
        r = http_json(args.base_url, "GET", f"/api/v1/ai/review-tickets/{ticket_id}", token=token)
        add("RT-29", "复核闭环", "工单表", "详情页", "GET /review-tickets/{id}", r, r.status == 200, f"status={(data_of(r) or {}).get('status')}")
        r = http_json(args.base_url, "POST", f"/api/v1/ai/review-tickets/{ticket_id}/start-review", None, token)
        add("RT-30", "复核闭环", "PENDING", "IN_REVIEW", "POST /start-review", r, r.status == 200, f"status={(data_of(r) or {}).get('status')}")
        r = http_json(args.base_url, "POST", f"/api/v1/ai/review-tickets/{ticket_id}/resolve", {"resolutionCode": "UNABLE_TO_CONFIRM", "reviewNote": "联调测试"}, token)
        add("RT-31", "复核闭环", "IN_REVIEW", "RESOLVED", "POST /resolve", r, r.status == 200, f"status={(data_of(r) or {}).get('status')}")
    r = http_json(args.base_url, "POST", "/api/v1/ai/rag/search-test", {"query": f"复核工单 {ticket_id} 中华白海豚", "limit": 5}, token)
    add("RT-32", "复核闭环", "复核工单", "RAG检索", "POST /rag/search-test", r, r.status == 200, f"hits={len(data_of(r) or [])}")
    r = http_json(args.base_url, "GET", "/api/v1/ai/rag/qdrant/status", token=token)
    add("RT-33", "收尾检查", "Qdrant", "RAG状态", "GET /rag/qdrant/status", r, r.status == 200, f"readyChunks={(data_of(r) or {}).get('readyChunks')}")
    r = http_json(args.base_url, "GET", "/api/v1/ai/review-tickets?page=1&size=1", token=token)
    add("RT-34", "收尾检查", "复核工单", "分页", "GET /review-tickets size=1", r, r.status == 200, f"items={len((data_of(r) or {}).get('items') or [])}")
    passed = sum(1 for row in rows[1:] if row[-1] == "通过")
    summary = [["项目", "值"], ["执行脚本", "run_integration_excel.ps1"], ["联调用例数", len(rows) - 1], ["通过数", passed], ["未通过数", len(rows) - 1 - passed], ["说明", "跨模块真实联调，记录上下游模块、接口、耗时和实际返回"]]
    output = Path(args.output_dir) / "04_真实系统联调执行报告.xlsx"
    write_xlsx(output, {"Summary": summary, "IntegrationCases": rows})
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", choices=["whitebox", "functional", "performance", "integration"], required=True)
    parser.add_argument("--output-dir", default=str(DEFAULT_REPORT_DIR))
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--frontend-url", default="http://localhost:5173")
    parser.add_argument("--image", default=r"C:\Users\13100\Pictures\OIP.jpg")
    args = parser.parse_args()
    if args.suite == "whitebox":
        output = run_whitebox(args)
    elif args.suite == "functional":
        output = run_functional(args)
    elif args.suite == "performance":
        output = run_performance(args)
    else:
        output = run_integration(args)
    print(output)


if __name__ == "__main__":
    main()
