import { EventLog } from 'types/aiops';
import { AiIncidentContext } from './aiopsApi';
import { normalizeIncidentAnalysis } from 'helpers/aiFallback';

const AI_SERVICE_URL =
  import.meta.env.VITE_AI_SERVICE_URL ?? (import.meta.env.DEV ? 'http://localhost:8001' : '');

export interface IncidentAnalysis {
  summary: string;
  priority: string;
  impact: string;
  risk: string;
  probable_causes: string[];
  suggested_actions: string[];
  diagnostic_commands: string[];
  provider: string;
  model?: string | null;
  requestedProvider?: string | null;
  requested_provider?: string | null;
  fallbackUsed?: boolean;
  fallback_used?: boolean;
  fallbackReasonCode?: string | null;
  fallback_reason_code?: string | null;
  fallbackReason?: string | null;
  fallback_reason?: string | null;
  cacheHit?: boolean;
  cache_hit?: boolean;
  analysisDurationMs?: number | null;
  analysis_duration_ms?: number | null;
  contextFingerprint?: string | null;
  context_fingerprint?: string | null;
}

export type IncidentAnalysisContext = AiIncidentContext;

export async function analyzeIncident(
  event: EventLog,
  context?: IncidentAnalysisContext | null,
): Promise<IncidentAnalysis> {
  const response = await fetch(`${AI_SERVICE_URL}/api/analyze-incident`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      eventType: event.eventType,
      severity: event.severity,
      message: event.message,
      details: event.details,
      deviceType: event.deviceType,
      deviceName: event.deviceName,
      location: event.location,
      occurredAt: event.occurredAt,
      incident: context,
    }),
  });

  if (!response.ok) {
    throw new Error(`AI service request failed: ${response.status} ${response.statusText}`);
  }

  return normalizeIncidentAnalysis((await response.json()) as IncidentAnalysis);
}
