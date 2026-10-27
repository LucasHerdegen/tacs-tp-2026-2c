FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    openssl \
    python3 \
    python3-cryptography \
    socat \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY bin/bin/tacs-linux-amd64 /app/tacs-linux-amd64
COPY legacy-clima/bootstrap_oauth.py /app/bootstrap_oauth.py
COPY legacy-clima/entrypoint.sh /app/entrypoint.sh

RUN sed -i 's/\r$//' /app/entrypoint.sh /app/bootstrap_oauth.py \
  && chmod +x /app/tacs-linux-amd64 /app/entrypoint.sh /app/bootstrap_oauth.py

EXPOSE 9978

ENTRYPOINT ["/app/entrypoint.sh"]
