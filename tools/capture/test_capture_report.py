"""退场证据与不生成媒体模式的报告边界回归。"""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.dont_write_bytecode = True
from PIL import Image
from assemble import analyse, event_exit_assertions, parse_meta, write_gallery


class CaptureReportTest(unittest.TestCase):
    def test_exit_evidence_must_exist_once_and_pass(self):
        good = [("saw-fragments-exits-continuously", True, "real samples"),
                ("torch-light-exits-continuously", True, "real samples")]
        self.assertTrue(all(row[1] for row in event_exit_assertions(good)))
        for broken in [[], good[:1], good + good[:1], [(good[0][0], False, "jump"), good[1]]]:
            with self.subTest(broken=broken):
                self.assertFalse(all(row[1] for row in event_exit_assertions(broken)))

    def test_no_video_never_invokes_encoder_and_gallery_omits_media(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            scene = root / "fixture"
            (scene / "frames").mkdir(parents=True)
            for i in range(2):
                Image.new("RGB", (1280, 720)).save(scene / "frames" / f"{i:05d}.png")
            (scene / "meta.tsv").write_text(
                "lengthTicks\t2\nframesDropped\t0\n"
                "check\tfixture\tt=0\tsaw-fragments-exits-continuously\tok=true\treal samples\n"
                "check\tfixture\tt=0\ttorch-light-exits-continuously\tok=true\treal samples\n", encoding="utf-8")
            with patch("assemble.build_video", side_effect=AssertionError("unexpected encoder")), \
                    patch("assemble.build_gif", side_effect=AssertionError("unexpected encoder")):
                result = analyse("fixture", {"length": 2, "title": "fixture", "metric": "white", "decay": "events"},
                                 root, [], no_video=True)
            self.assertFalse(result["media"])
            self.assertFalse(any("生成 mp4" == row[0] or "生成 gif" == row[0] for row in result["asserts"]))
            self.assertTrue((scene / "contact.png").is_file())
            write_gallery([result], root)
            html = (root / "index.html").read_text(encoding="utf-8")
            self.assertNotIn("<video", html)
            self.assertNotIn("fixture.gif", html)
            # 说明文字不能覆盖检查名称，坏行也不能默认为通过。
            _, checks = parse_meta(scene / "meta.tsv")
            self.assertEqual(checks[0][0], "saw-fragments-exits-continuously")
            (scene / "meta.tsv").write_text("check\tbroken\n", encoding="utf-8")
            self.assertEqual(parse_meta(scene / "meta.tsv")[1], [("malformed-check", False)])


if __name__ == "__main__":
    unittest.main()
