#!/usr/bin/env python3
import argparse
import configparser
import datetime
import hashlib
import io
import json
import re
import stat
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from pypdf import PdfReader
from pypdf.errors import PyPdfError


GROUP_B_TEAMS = {"T01", "T04", "T08", "T10", "T17", "T18"}
REQUIRED_SOURCE = {
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
}
PRIVATE_PARTS = {".git", ".idea", ".gradle", ".intellijPlatform", "course-materials", "index",
                 ".venv", "build", "out", "submission"}


def git(repo, *arguments):
    result = subprocess.run(
        ["git", "-C", str(repo), *arguments], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    return result.stdout


def normalized(text):
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", text).casefold())


def read_team(path):
    team = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(team, dict) or team.get("teamNo") not in GROUP_B_TEAMS:
        raise ValueError("团队编号应为 B 组的 T01、T04、T08、T10、T17 或 T18。")
    members = team.get("members")
    if not isinstance(members, list) or not members:
        raise ValueError("请提供团队成员资料。")
    identifiers = set()
    for member in members:
        if not isinstance(member, dict):
            raise ValueError("每位成员应使用 JSON 对象表示。")
        for key in ("fullName", "matriculationNumber", "contactNumber", "email"):
            value = member.get(key)
            if (
                not isinstance(value, str)
                or not value.strip()
                or value.strip().casefold() in {"todo", "tbd", "待填", "pending", "..."}
                or (value.strip().startswith("[") and value.strip().endswith("]"))
            ):
                raise ValueError(f"成员字段 {key} 应填写真实资料。")
        if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", member["email"]):
            raise ValueError("成员邮箱格式有误。")
        identifier = normalized(member["matriculationNumber"])
        if identifier in identifiers:
            raise ValueError("成员学号出现重复。")
        identifiers.add(identifier)
    return team


def read_pdf(path):
    path = Path(path)
    if path.suffix.lower() != ".pdf" or not path.is_file() or path.stat().st_size > 50_000_000:
        raise ValueError("提交文档应为小于 50 MB 的 PDF 文件。")
    reader = PdfReader(path)
    if reader.is_encrypted or not reader.pages:
        raise ValueError("提交文档应为可直接打开的 PDF。")
    return [page.extract_text() or "" for page in reader.pages]


def validate_documents(report, slides, team):
    if Path(report).resolve() == Path(slides).resolve():
        raise ValueError("报告与演示幻灯片应分别提供。")
    report_text = normalized("\n".join(read_pdf(report)))
    first_slide = normalized(read_pdf(slides)[0])
    for member in team["members"]:
        for key in ("fullName", "matriculationNumber", "contactNumber", "email"):
            if normalized(member[key]) not in report_text:
                raise ValueError(f"报告缺少成员字段：{key}。")
        for key in ("fullName", "matriculationNumber"):
            if normalized(member[key]) not in first_slide:
                raise ValueError(f"演示首页缺少成员字段：{key}。")


def plugin_version(path):
    versions = []
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.endswith(".jar") and "/lib/" in name:
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as library:
                    if "META-INF/plugin.xml" in library.namelist():
                        descriptor = ET.fromstring(library.read("META-INF/plugin.xml"))
                        if descriptor.findtext("id") == "edu.cst3492.taide":
                            versions.append(descriptor.findtext("version"))
    if len(versions) != 1 or not versions[0]:
        raise ValueError("插件 ZIP 应包含一个已填充版本号的 TAIDE 插件入口。")
    return versions[0]


def package(repo, report, slides, team_file, plugin, output):
    repo = Path(repo).resolve()
    output = Path(output).resolve()
    if output.suffix.lower() != ".zip" or output.exists():
        raise ValueError("请指定尚未存在的 ZIP 输出路径。")
    if git(repo, "status", "--porcelain", "--untracked-files=all").strip():
        raise ValueError("源码工作区应保持干净，请先提交源码和文档。")
    team = read_team(team_file)
    validate_documents(report, slides, team)
    version = plugin_version(plugin)
    properties = configparser.ConfigParser(interpolation=None)
    properties.read_string("[plugin]\n" + (repo / "gradle.properties").read_text(encoding="utf-8"))
    if version != properties.get("plugin", "pluginVersion"):
        raise ValueError("插件版本与 gradle.properties 中的 pluginVersion 应保持一致。")
    paths = git(repo, "ls-files", "-z").decode("utf-8").split("\0")
    paths = [name for name in paths if name]
    if not REQUIRED_SOURCE.issubset(paths):
        raise ValueError("源码缺少 Gradle Wrapper 或构建配置。")
    if not any(name.startswith("src/main/java/") and name.endswith(".java") for name in paths):
        raise ValueError("源码缺少 Java 实现。")
    entries = {}
    for name in paths:
        relative = Path(name)
        source = repo / relative
        if source.is_symlink() or not source.is_file():
            raise ValueError(f"源码应为普通文件：{name}")
        if relative.parts[0] in PRIVATE_PARTS or relative.name in {".env", "local.properties"}:
            raise ValueError(f"源码中包含本地私有配置或材料：{name}")
        target = "documents/reference/" + name[5:] if name.startswith("docs/") else "source/" + name
        mode = 0o755 if source.stat().st_mode & stat.S_IXUSR else 0o644
        entries[target] = (source.read_bytes(), mode)
    entries["documents/report.pdf"] = (Path(report).read_bytes(), 0o644)
    entries["documents/slides.pdf"] = (Path(slides).read_bytes(), 0o644)
    entries["plugin/" + Path(plugin).name] = (Path(plugin).read_bytes(), 0o644)
    manifest = {
        "teamNo": team["teamNo"],
        "group": "B",
        "gitCommit": git(repo, "rev-parse", "HEAD").decode().strip(),
        "pluginVersion": version,
        "sha256": {name: hashlib.sha256(data).hexdigest() for name, (data, _) in sorted(entries.items())},
    }
    entries["manifest.json"] = ((json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8"), 0o644)
    commit_time = int(git(repo, "show", "-s", "--format=%ct", "HEAD"))
    timestamp = datetime.datetime.fromtimestamp(max(commit_time, 315532800), datetime.timezone.utc)
    output.parent.mkdir(parents=True, exist_ok=True)
    # 固定时间戳与文件顺序，同一组输入可复现完全相同的提交包。
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, (data, mode) in sorted(entries.items()):
            info = zipfile.ZipInfo(name, timestamp.timetuple()[:6])
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | mode) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data, compresslevel=9)
    return output


def main():
    parser = argparse.ArgumentParser(description="核对 B 组资料并打包 Canvas 提交 ZIP。")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--slides", type=Path, required=True)
    parser.add_argument("--team-file", type=Path, required=True)
    parser.add_argument("--plugin", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        output = package(args.repo, args.report, args.slides, args.team_file, args.plugin, args.output)
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile,
            ET.ParseError, PyPdfError, configparser.Error) as error:
        print(f"提交包检查失败：{error}", file=sys.stderr)
        return 1
    print(f"提交包：{output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
