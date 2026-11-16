from .base import BookCheckResult
from datetime import datetime, timezone

class DemoMomoxProvider:
    """Safe placeholder. Does not contact Momox and makes no purchase claims."""
    def check(self, isbn: str) -> BookCheckResult:
        return BookCheckResult(
            isbn=isbn,
            status="UNKNOWN",
            checked_at=int(datetime.now(timezone.utc).timestamp() * 1000),
            message="Demo-Modus: Momox-Schnittstelle noch nicht angebunden."
        )
