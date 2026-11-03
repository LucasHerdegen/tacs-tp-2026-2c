#!/usr/bin/env python3
import base64
import datetime
import http.server
import json
import os
import ssl
import time
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.x509.oid import NameOID

CLIENT_ID = "235030233089-137pks34ei7rahu2mu2t02mh1917139k.apps.googleusercontent.com"
KID = "tacs-legacy-key-1"
EMAIL = os.environ.get("LEGACY_CLIMA_ACCOUNT_EMAIL", "tintili@frba.utn.edu.ar")
CERT_PATH = "/tmp/oauth-bootstrap-cert.pem"
KEY_PATH = "/tmp/oauth-bootstrap-key.pem"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def b64url_uint(val: int) -> str:
    length = (val.bit_length() + 7) // 8
    return b64url(val.to_bytes(length, "big"))


private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
public_numbers = private_key.public_key().public_numbers()

subject = issuer = x509.Name(
    [x509.NameAttribute(NameOID.COMMON_NAME, "oauth2.googleapis.com")]
)
now = datetime.datetime.now(datetime.timezone.utc)
cert = (
    x509.CertificateBuilder()
    .subject_name(subject)
    .issuer_name(issuer)
    .public_key(private_key.public_key())
    .serial_number(x509.random_serial_number())
    .not_valid_before(now - datetime.timedelta(days=1))
    .not_valid_after(now + datetime.timedelta(days=365))
    .add_extension(
        x509.SubjectAlternativeName(
            [
                x509.DNSName("oauth2.googleapis.com"),
                x509.DNSName("www.googleapis.com"),
            ]
        ),
        critical=False,
    )
    .sign(private_key, hashes.SHA256())
)

with open(CERT_PATH, "wb") as f:
    f.write(cert.public_bytes(serialization.Encoding.PEM))

with open(KEY_PATH, "wb") as f:
    f.write(
        private_key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.TraditionalOpenSSL,
            serialization.NoEncryption(),
        )
    )


def build_id_token() -> str:
    iat = int(time.time())
    header = {"alg": "RS256", "kid": KID, "typ": "JWT"}
    payload = {
        "iss": "https://accounts.google.com",
        "aud": CLIENT_ID,
        "sub": "109876543210",
        "email": EMAIL,
        "email_verified": True,
        "hd": EMAIL.split("@")[-1],
        "iat": iat - 60,
        "exp": iat + 3600,
    }
    signing_input = (
        f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(payload).encode())}"
    )
    signature = private_key.sign(
        signing_input.encode("ascii"), padding.PKCS1v15(), hashes.SHA256()
    )
    return f"{signing_input}.{b64url(signature)}"


class OAuthBootstrapHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_GET(self):
        if self.path.startswith("/oauth2/v3/certs"):
            jwks = {
                "keys": [
                    {
                        "kty": "RSA",
                        "alg": "RS256",
                        "use": "sig",
                        "kid": KID,
                        "n": b64url_uint(public_numbers.n),
                        "e": b64url_uint(public_numbers.e),
                    }
                ]
            }
            body = json.dumps(jwks).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length > 0:
            self.rfile.read(length)
        resp = {
            "access_token": "ya29.headless-container-session",
            "token_type": "Bearer",
            "expires_in": 3600,
            "refresh_token": "1//headless-refresh-token",
            "id_token": build_id_token(),
        }
        body = json.dumps(resp).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main():
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 443), OAuthBootstrapHandler)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(certfile=CERT_PATH, keyfile=KEY_PATH)
    server.socket = ctx.wrap_socket(server.socket, server_side=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
