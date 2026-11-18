"""Regression checks for the Android dashboard's compact cloud header."""
import re
import unittest
from pathlib import Path


SOURCE = (Path(__file__).resolve().parents[1]
          / "sync-app/app/src/main/java/com/tod4s3/sync/MainActivity.java")


class CloudHeaderRegressionTests(unittest.TestCase):
    def test_cloud_indicator_has_a_small_explicit_height(self):
        source = SOURCE.read_text(encoding="utf-8")
        toolbar = source.split("private LinearLayout dashboardToolbar()", 1)[1].split(
            "private LinearLayout dashboardTabs()", 1
        )[0]
        self.assertIn('text("Nube ToDus"', toolbar)
        self.assertIn('text("Uso de espacio y entregas"', toolbar)
        layout = re.search(
            r"LinearLayout\.LayoutParams\s+cloudProgressLayout\s*=\s*"
            r"new\s+LinearLayout\.LayoutParams\s*\(\s*-1\s*,\s*dp\((\d+)\)\s*\)",
            toolbar,
        )
        self.assertIsNotNone(layout, "The cloud bar must not use WRAP_CONTENT for height")
        self.assertGreaterEqual(int(layout.group(1)), 1)
        self.assertLessEqual(int(layout.group(1)), 12)
        self.assertIn("bar.addView(cloudProgress, cloudProgressLayout);", toolbar)
        self.assertNotIn("bar.addView(cloudProgress, margin(", toolbar)

    def test_plan_and_tabs_follow_the_toolbar(self):
        source = SOURCE.read_text(encoding="utf-8")
        dashboard = source.split("private void showDashboard", 1)[1].split(
            "private LinearLayout buildDashboardPlanCard", 1
        )[0]
        self.assertLess(dashboard.index("page.addView(dashboardToolbar()"),
                        dashboard.index("page.addView(dashboardPlanCard"))
        self.assertLess(dashboard.index("page.addView(dashboardPlanCard"),
                        dashboard.index("page.addView(dashboardTabs()"))


if __name__ == "__main__":
    unittest.main()
