"""Regression checks for the Android dashboard's compact cloud header."""
import re
import unittest
from pathlib import Path


SOURCE = (Path(__file__).resolve().parents[1]
          / "sync-app/app/src/main/java/com/tod4s3/sync/MainActivity.java")


class CloudHeaderRegressionTests(unittest.TestCase):
    def test_dashboard_toolbar_does_not_render_a_full_height_cloud_usage_panel(self):
        source = SOURCE.read_text(encoding="utf-8")
        toolbar = source.split("private LinearLayout dashboardToolbar()", 1)[1].split(
            "private LinearLayout dashboardTabs()", 1
        )[0]
        self.assertNotIn('text("Nube ToDus"', toolbar)
        self.assertNotIn('text("Uso de espacio y entregas"', toolbar)
        self.assertNotIn("cloudProgress", toolbar)

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
