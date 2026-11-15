from fastapi import FastAPI
from pydantic import BaseModel, Field
from app.providers.demo import DemoMomoxProvider

app = FastAPI(title="BuchRadar API", version="0.1.0")
provider = DemoMomoxProvider()

class CheckRequest(BaseModel):
    isbns: list[str] = Field(min_length=1, max_length=500)

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/v1/books/check")
def check_books(request: CheckRequest):
    return {"results": [provider.check(isbn).model_dump() for isbn in request.isbns]}
