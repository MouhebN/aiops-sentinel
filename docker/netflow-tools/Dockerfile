FROM debian:stable-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends nfdump ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /data/netflow

EXPOSE 2055/udp

CMD ["sh","-c","nfcapd -l /data/netflow -p 2055 -D && tail -f /dev/null"]
