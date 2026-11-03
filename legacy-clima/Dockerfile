FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gzip \
    openssl \
    python3 \
    python3-cryptography \
    socat \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY tacs-linux-amd64.gz /app/tacs-linux-amd64.gz
COPY bootstrap_oauth.py /app/bootstrap_oauth.py
COPY entrypoint.sh /app/entrypoint.sh

RUN gzip -dc /app/tacs-linux-amd64.gz > /app/tacs-linux-amd64 \
  && rm -f /app/tacs-linux-amd64.gz \
  && sed -i 's/\r$//' /app/entrypoint.sh /app/bootstrap_oauth.py \
  && chmod +x /app/tacs-linux-amd64 /app/entrypoint.sh /app/bootstrap_oauth.py

EXPOSE 9978

ENTRYPOINT ["/app/entrypoint.sh"]
