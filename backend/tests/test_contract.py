import time
import unittest

from pydantic import ValidationError
from app.main import CheckRequest, check_books


class CheckContractTest(unittest.TestCase):
    def test_duplicates_are_checked_once(self):
        request = CheckRequest(isbns=["9780804429573", "9780804429573"])
        self.assertEqual(request.isbns, ["9780804429573"])

    def test_bad_checksum_is_rejected(self):
        with self.assertRaises(ValidationError):
            CheckRequest(isbns=["9780804429574"])

    def test_non_book_ean_is_rejected(self):
        with self.assertRaises(ValidationError):
            CheckRequest(isbns=["4006381333931"])

    def test_empty_and_oversized_requests_are_rejected(self):
        for identifiers in [[], ["9780804429573"] * 501]:
            with self.subTest(size=len(identifiers)), self.assertRaises(ValidationError):
                CheckRequest(isbns=identifiers)

    def test_demo_never_claims_an_offer_and_uses_milliseconds(self):
        before = int(time.time() * 1000)
        result = check_books(CheckRequest(isbns=["9780804429573"]))["results"][0]
        self.assertEqual(result["status"], "UNKNOWN")
        self.assertIsNone(result["price_cents"])
        self.assertGreaterEqual(result["checked_at"], before)
        self.assertLessEqual(result["checked_at"], int(time.time() * 1000))


if __name__ == "__main__":
    unittest.main()
