FROM python:3.12-alpine
WORKDIR /app
RUN apk add --no-cache tcpdump coreutils \
    && mkdir -p /tmp/captures /tmp/captures/ring
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
ENV CAPTURE_DIR=/tmp/captures
ENV CAPTURE_ALLOWED_INTERFACES=eth1
ENV CAPTURE_MAX_DURATION_SECONDS=60
ENV CAPTURE_MAX_FILE_MB=20
ENV CAPTURE_MAX_CONCURRENT=1
ENV ROLLING_ENABLED=true
ENV ROLLING_INTERFACE=eth1
ENV ROLLING_SEGMENT_SECONDS=5
ENV ROLLING_PRE_TRIGGER_SECONDS=60
ENV ROLLING_POST_TRIGGER_SECONDS=20
ENV ROLLING_MAX_FILE_MB=20
ENV ROLLING_DIR=/tmp/captures/ring
EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
