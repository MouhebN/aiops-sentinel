import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from app.config import log_startup
from app.schemas import IncidentAnalysisRequest, IncidentAnalysisResponse, PacketCaptureAnalysisResponse
from app.services.analyzer import analyze
from app.services.ollama_client import get_provider_status
from app.services.pcap_analyzer import analyze_pcap


logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    log_startup()
    yield


app = FastAPI(
    title="AIOps AI Service",
    description="Incident explanation and remediation suggestion service for the AIOps Supervision Platform.",
    version="0.2.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "aiops-ai-service"}


@app.get("/api/ai/provider-status")
def provider_status() -> dict:
    return get_provider_status()


@app.post("/api/analyze-incident", response_model=IncidentAnalysisResponse)
def analyze_incident(request: IncidentAnalysisRequest) -> IncidentAnalysisResponse:
    return analyze(request)


@app.post("/api/analyze-pcap", response_model=PacketCaptureAnalysisResponse)
def analyze_packet_capture(file: UploadFile = File(...)) -> PacketCaptureAnalysisResponse:
    return analyze_pcap(file)
