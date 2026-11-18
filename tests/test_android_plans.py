"""Regression checks for Android plan selection and activation controls."""
import re
import unittest
from pathlib import Path


SOURCE = (Path(__file__).resolve().parents[1]
          / "sync-app/app/src/main/java/com/tod4s3/sync/MainActivity.java")


def section(start, end):
    source = SOURCE.read_text(encoding="utf-8")
    return source.split(start, 1)[1].split(end, 1)[0]


class PlansScreenRegressionTests(unittest.TestCase):
    def test_each_premium_plan_has_its_own_correct_purchase_request(self):
        plans = section("private void showPlansStep(", "private LinearLayout planOffer(")
        offers = re.findall(
            r'page\.addView\(planOffer\("([^"]+)",\s*"([^"]+)",\s*'
            r'"[^"]+",\s*"([^"]+)"\)', plans,
        )
        self.assertEqual(offers, [
            ("Premium 15 días", "250 CUP", "Premium 15 días · 250 CUP"),
            ("Premium 20 días", "300 CUP", "Premium 20 días · 300 CUP"),
            ("Premium 30 días", "500 CUP", "Premium 30 días · 500 CUP"),
        ])
        self.assertNotIn("premiumShowcase()", plans)
        self.assertIn('text("Gratis"', plans)
        self.assertIn('verifiedPremium ? "DISPONIBLE" : "TU PLAN"', plans)
        self.assertIn("@Niku_Blade", plans)
        self.assertIn("@fefefelix", plans)

    def test_each_plan_has_large_full_width_accessible_buttons(self):
        offer = section("private LinearLayout planOffer(", "private void animatePlanSections(")
        self.assertIn('goldButton("Comprar premium")', offer)
        self.assertIn('secondaryButton("Ya soy premium")', offer)
        self.assertIn("buyPlan.setTextSize(16)", offer)
        self.assertIn("buyPlan.setMinHeight(dp(52))", offer)
        self.assertIn("actions.addView(buyPlan, new LinearLayout.LayoutParams(-1, -2))", offer)
        self.assertIn("alreadyPremium.setTextSize(16)", offer)
        self.assertIn("alreadyPremium.setMinHeight(dp(52))", offer)
        self.assertIn("alreadyPremium.setBackground(roundRect(", offer)
        self.assertIn("actions.addView(alreadyPremium, margin(0, 8, 0, 0))", offer)
        self.assertIn("openPremiumRequest(purchasePlan)", offer)
        self.assertIn("promptPremiumActivationToken()", offer)

    def test_payment_labels_remain_readable_on_dark_chips(self):
        chip = section("private LinearLayout paymentMethodChip(", "private void openPremiumRequest(")
        self.assertIn("labelView.setTextColor(Color.WHITE)", chip)
        self.assertIn("new LinearLayout.LayoutParams(0, -2, 1f)", chip)


if __name__ == "__main__":
    unittest.main()
