# Bank lab IP camera. Build once (needs Internet). Runtime is offline.
#   docker build -t banklab-camera:local ./services/camera

FROM python:3.12-alpine

ARG MTX_VERSION=1.11.3
ARG TARGETARCH

RUN apk add --no-cache ffmpeg iproute2 ca-certificates wget \
    && mtx_arch="${TARGETARCH}" \
    && if [ -z "$mtx_arch" ]; then \
         case "$(uname -m)" in \
           x86_64) mtx_arch=amd64 ;; \
           aarch64|arm64) mtx_arch=arm64 ;; \
           *) echo "unsupported arch $(uname -m)" >&2; exit 1 ;; \
         esac; \
       fi \
    && wget -qO /tmp/mediamtx.tar.gz \
         "https://github.com/bluenviron/mediamtx/releases/download/v${MTX_VERSION}/mediamtx_v${MTX_VERSION}_linux_${mtx_arch}.tar.gz" \
    && tar -xzf /tmp/mediamtx.tar.gz -C /usr/local/bin mediamtx \
    && rm -f /tmp/mediamtx.tar.gz \
    && chmod +x /usr/local/bin/mediamtx \
    && apk del wget \
    && command -v ffmpeg \
    && test -x /usr/local/bin/mediamtx

# PID 1 is overridden by Containerlab (`sleep infinity`). Scripts are bind-mounted.
CMD ["sleep", "infinity"]
