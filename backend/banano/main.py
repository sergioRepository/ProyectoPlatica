from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel

from banano import __version__

app = FastAPI(title="lavidanoesunbanano-api", version=__version__)


class HealthResponse(BaseModel):
    status: Literal["ok"]
    version: str


@app.get("/health")
def health() -> HealthResponse:
    return HealthResponse(status="ok", version=__version__)
