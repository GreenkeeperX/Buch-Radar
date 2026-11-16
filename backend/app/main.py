from fastapi import FastAPI
from pydantic import BaseModel, Field, field_validator
from typing import Annotated
from app.providers.demo import DemoMomoxProvider

app = FastAPI(title="BuchRadar API", version="0.1.0")
provider = DemoMomoxProvider()

class CheckRequest(BaseModel):
    isbns: list[Annotated[str, Field(pattern=r"^97[89][0-9]{10}$")]] = Field(min_length=1, max_length=500)

    @field_validator("isbns")
    @classmethod
    def validate_identifiers(cls, values):
        for value in values:
            checksum = sum(int(c) * (1 if i % 2 == 0 else 3) for i, c in enumerate(value[:12]))
            if (10 - checksum % 10) % 10 != int(value[-1]):
                raise ValueError("Invalid ISBN checksum")
        return list(dict.fromkeys(values))

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/v1/books/check")
def check_books(request: CheckRequest):
    return {"results": [provider.check(isbn).model_dump() for isbn in request.isbns]}
