import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

from reportlab.pdfgen import canvas

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from package_submission import package, read_team, validate_documents


class PackageSubmissionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.git("init")
        self.git("config", "user.name", "TAIDE Test")
        self.git("config", "user.email", "taide-test@example.invalid")
        self.files = {
            "build.gradle.kts": "plugins { java }\n",
            "settings.gradle.kts": 'rootProject.name = "taide"\n',
            "gradle.properties": "pluginVersion=0.1.0-SNAPSHOT\n",
            "gradlew": "#!/bin/sh\n",
            "gradlew.bat": "@echo off\n",
            "gradle/wrapper/gradle-wrapper.jar": "wrapper fixture",
            "gradle/wrapper/gradle-wrapper.properties": "distributionUrl=fixture\n",
            "src/main/java/example/Action.java": "class Action {}\n",
            "docs/AUTOMATION.md": "Automation technical notes\n",
        }
        for name, text in self.files.items():
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        (self.repo / "gradlew").chmod(0o755)
        self.git("add", ".")
        self.git("commit", "-m", "chore: add packaging fixture")
        self.team = {
            "teamNo": "T04",
            "members": [{
                "fullName": "Example Student",
                "matriculationNumber": "TEST12345",
                "contactNumber": "12345678",
                "email": "student@example.invalid",
            }],
        }
        self.team_file = self.root / "team.json"
        self.team_file.write_text(json.dumps(self.team), encoding="utf-8")
        self.report = self.root / "report.pdf"
        self.slides = self.root / "slides.pdf"
        self.pdf(self.report, ["Example Student TEST12345", "12345678 student@example.invalid", "System Architecture"])
        self.pdf(self.slides, ["TAIDE", "Example Student TEST12345"])
        self.plugin = self.root / "taide.zip"
        self.make_plugin("0.1.0-SNAPSHOT")

    def git(self, *args):
        return subprocess.run(
            ["git", "-C", str(self.repo), *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
        ).stdout

    def pdf(self, path, lines):
        document = canvas.Canvas(str(path))
        for position, text in enumerate(lines):
            document.drawString(40, 780 - position * 20, text)
        document.save()

    def make_plugin(self, version):
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/plugin.xml",
                             f"<idea-plugin><id>edu.cst3492.taide</id><version>{version}</version></idea-plugin>")
        with zipfile.ZipFile(self.plugin, "w") as archive:
            archive.writestr("taide/lib/taide.jar", jar.getvalue())

    def build(self, name="submission.zip"):
        return package(self.repo, self.report, self.slides, self.team_file, self.plugin, self.root / name)

    def test_reproducible_package_has_separate_folders_and_checksums(self):
        first = self.build("first.zip")
        second = self.build("second.zip")
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as archive:
            self.assertIn("source/src/main/java/example/Action.java", archive.namelist())
            self.assertIn("documents/report.pdf", archive.namelist())
            self.assertIn("documents/slides.pdf", archive.namelist())
            self.assertIn("documents/reference/AUTOMATION.md", archive.namelist())
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual("B", manifest["group"])
            self.assertEqual("0.1.0-SNAPSHOT", manifest["pluginVersion"])
            self.assertIn("source/gradlew", manifest["sha256"])
            self.assertEqual(0o755, archive.getinfo("source/gradlew").external_attr >> 16 & 0o777)
        with self.assertRaises(ValueError):
            self.build("first.zip")

    def test_rejects_dirty_source(self):
        (self.repo / "gradle.properties").write_text("pluginVersion=changed\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "干净"):
            self.build()

    def test_rejects_private_tracked_file(self):
        (self.repo / ".env").write_text("fixture=true\n", encoding="utf-8")
        self.git("add", ".env")
        self.git("commit", "-m", "test: add rejected private-file fixture")
        with self.assertRaisesRegex(ValueError, "私有"):
            self.build()

    def test_checks_first_slide_and_report_identity(self):
        self.pdf(self.slides, ["TAIDE"])
        with self.assertRaisesRegex(ValueError, "演示首页"):
            validate_documents(self.report, self.slides, self.team)
        self.pdf(self.slides, ["Example Student TEST12345"])
        self.pdf(self.report, ["Example Student TEST12345"])
        with self.assertRaisesRegex(ValueError, "报告缺少"):
            validate_documents(self.report, self.slides, self.team)

    def test_rejects_wrong_group_and_incomplete_roster(self):
        self.team["teamNo"] = "T03"
        self.team_file.write_text(json.dumps(self.team), encoding="utf-8")
        with self.assertRaises(ValueError):
            read_team(self.team_file)
        self.team["teamNo"] = "T04"
        self.team["members"][0]["fullName"] = "[pending]"
        self.team_file.write_text(json.dumps(self.team), encoding="utf-8")
        with self.assertRaises(ValueError):
            read_team(self.team_file)

    def test_rejects_mismatched_plugin_version(self):
        self.make_plugin("9.9.9")
        with self.assertRaisesRegex(ValueError, "版本"):
            self.build()

    def test_accepts_real_names_containing_placeholder_substrings(self):
        self.team["members"][0]["fullName"] = "Example Todorov"
        self.team_file.write_text(json.dumps(self.team), encoding="utf-8")
        self.assertEqual("Example Todorov", read_team(self.team_file)["members"][0]["fullName"])

    def test_rejects_missing_gradle_wrapper(self):
        self.git("rm", "gradlew")
        self.git("commit", "-m", "chore: remove gradlew")
        with self.assertRaisesRegex(ValueError, "缺少 Gradle Wrapper"):
            self.build()

    def test_rejects_missing_java_implementation(self):
        self.git("rm", "src/main/java/example/Action.java")
        self.git("commit", "-m", "chore: remove java files")
        with self.assertRaisesRegex(ValueError, "缺少 Java 实现"):
            self.build()


if __name__ == "__main__":
    unittest.main()
