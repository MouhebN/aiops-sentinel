import { useCallback, useEffect, useState } from 'react';
import { aiopsApi, MetricSample, MonitoredComponent } from 'api/aiopsApi';
import { Device, EventLog } from 'types/aiops';

export const useAiopsData = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [components, setComponents] = useState<MonitoredComponent[]>([]);
  const [events, setEvents] = useState<EventLog[]>([]);
  const [alerts, setAlerts] = useState<EventLog[]>([]);
  const [metrics, setMetrics] = useState<MetricSample[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setError(null);
      const [devicesResponse, componentsResponse, eventsResponse, alertsResponse] =
        await Promise.all([
          aiopsApi.getDevices(),
          aiopsApi.getComponents(),
          aiopsApi.getEvents(),
          aiopsApi.getAlerts(),
        ]);
      const metricResponses = await Promise.all(
        componentsResponse.map((component) => aiopsApi.getComponentMetrics(component.id, 24)),
      );
      setDevices(devicesResponse);
      setComponents(componentsResponse);
      setEvents(eventsResponse);
      setAlerts(alertsResponse);
      setMetrics(metricResponses.flat());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown API error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const intervalId = window.setInterval(refresh, 5000);
    return () => window.clearInterval(intervalId);
  }, [refresh]);

  return { devices, components, events, alerts, metrics, loading, error, refresh };
};
