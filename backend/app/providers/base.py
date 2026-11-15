from typing import Protocol
from pydantic import BaseModel

class BookCheckResult(BaseModel):
    isbn: str
    status: str  # AVAILABLE, NOT_AVAILABLE, UNKNOWN, ERROR
    price_cents: int | None = None
    checked_at: int | None = None
    message: str | None = None

class MomoxProvider(Protocol):
    def check(self, isbn: str) -> BookCheckResult: ...
