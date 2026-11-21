#!/usr/bin/env python3
"""Local web editor for liveperf library JSON (groups → setlists → songs → instruments)."""

from __future__ import annotations

import argparse
import json
import mimetypes
import re
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEBEDIT = Path(__file__).resolve().parent
STATIC = WEBEDIT / "static"
LIBRARIES = ROOT / "libraries"
CATALOG = ROOT / "v3sound-grandpianopro.json"

SAFE_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,120}\.json$")


def ensure_dirs() -> None:
    LIBRARIES.mkdir(parents=True, exist_ok=True)
    example = ROOT / "examples" / "library-example.json"
    seed = LIBRARIES / "library.json"
    if not seed.exists() and example.exists():
        seed.write_text(example.read_text(encoding="utf-8"), encoding="utf-8")


def list_library_files() -> list[dict]:
    files = []
    for path in sorted(LIBRARIES.glob("*.json")):
        if not SAFE_NAME.match(path.name):
            continue
        files.append(
            {
                "name": path.name,
                "size": path.stat().st_size,
                "mtime": int(path.stat().st_mtime),
            }
        )
    return files


def library_path(name: str) -> Path | None:
    if not SAFE_NAME.match(name):
        return None
    path = (LIBRARIES / name).resolve()
    if path.parent != LIBRARIES.resolve():
        return None
    return path


class Handler(BaseHTTPRequestHandler):
    server_version = "liveperf-webedit/1.0"

    def log_message(self, fmt: str, *args) -> None:
        print(f"[{self.log_date_time_string()}] {self.address_string()} {fmt % args}")

    def _cors(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, code: int, payload) -> None:
        body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _text(self, code: int, text: str, content_type: str = "text/plain; charset=utf-8") -> None:
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _bytes(self, code: int, data: bytes, content_type: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self._cors()
        self.end_headers()
        self.wfile.write(data)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length else b"{}"
        return json.loads(raw.decode("utf-8"))

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path in ("/", "/index.html"):
            self._serve_static("index.html")
            return
        if path.startswith("/static/"):
            self._serve_static(path[len("/static/") :])
            return

        if path == "/api/catalog":
            if not CATALOG.exists():
                self._json(404, {"error": "Catálogo V3 no encontrado"})
                return
            data = json.loads(CATALOG.read_text(encoding="utf-8"))
            self._json(200, data)
            return

        if path == "/api/files":
            self._json(200, {"files": list_library_files(), "dir": str(LIBRARIES)})
            return

        if path.startswith("/api/files/"):
            name = urllib.parse.unquote(path[len("/api/files/") :])
            target = library_path(name)
            if target is None:
                self._json(400, {"error": "Nombre de fichero no válido"})
                return
            if not target.exists():
                self._json(404, {"error": "Fichero no encontrado"})
                return
            try:
                data = json.loads(target.read_text(encoding="utf-8"))
            except json.JSONDecodeError as exc:
                self._json(500, {"error": f"JSON inválido: {exc}"})
                return
            self._json(200, {"name": name, "library": data})
            return

        self._json(404, {"error": "Not found"})

    def do_PUT(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if not parsed.path.startswith("/api/files/"):
            self._json(404, {"error": "Not found"})
            return
        name = urllib.parse.unquote(parsed.path[len("/api/files/") :])
        target = library_path(name)
        if target is None:
            self._json(400, {"error": "Nombre de fichero no válido (usa *.json seguro)"})
            return
        try:
            payload = self._read_json()
        except json.JSONDecodeError as exc:
            self._json(400, {"error": f"JSON inválido: {exc}"})
            return
        library = payload.get("library", payload)
        if not isinstance(library, dict) or "groups" not in library:
            self._json(400, {"error": "Se espera un objeto con clave groups"})
            return
        text = json.dumps(library, ensure_ascii=False, indent=2) + "\n"
        target.write_text(text, encoding="utf-8")
        self._json(200, {"ok": True, "name": name, "path": str(target)})

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/api/files":
            self._json(404, {"error": "Not found"})
            return
        try:
            payload = self._read_json()
        except json.JSONDecodeError as exc:
            self._json(400, {"error": f"JSON inválido: {exc}"})
            return
        name = (payload.get("name") or "").strip()
        if not name.endswith(".json"):
            name += ".json"
        target = library_path(name)
        if target is None:
            self._json(400, {"error": "Nombre no válido"})
            return
        if target.exists() and not payload.get("overwrite"):
            self._json(409, {"error": "Ya existe; usa overwrite o otro nombre"})
            return
        library = payload.get("library") or {"groups": []}
        text = json.dumps(library, ensure_ascii=False, indent=2) + "\n"
        target.write_text(text, encoding="utf-8")
        self._json(201, {"ok": True, "name": name, "path": str(target)})

    def do_DELETE(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if not parsed.path.startswith("/api/files/"):
            self._json(404, {"error": "Not found"})
            return
        name = urllib.parse.unquote(parsed.path[len("/api/files/") :])
        target = library_path(name)
        if target is None:
            self._json(400, {"error": "Nombre no válido"})
            return
        if not target.exists():
            self._json(404, {"error": "No existe"})
            return
        target.unlink()
        self._json(200, {"ok": True, "name": name})

    def _serve_static(self, rel: str) -> None:
        rel = rel.lstrip("/")
        if ".." in rel or rel.startswith("/"):
            self._text(400, "Bad path")
            return
        path = (STATIC / rel).resolve()
        if not str(path).startswith(str(STATIC.resolve())) or not path.is_file():
            self._text(404, "Not found")
            return
        ctype = mimetypes.guess_type(str(path))[0] or "application/octet-stream"
        self._bytes(200, path.read_bytes(), ctype)


def main() -> None:
    parser = argparse.ArgumentParser(description="Editor web local liveperf")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    ensure_dirs()
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"liveperf webedit → http://{args.host}:{args.port}/")
    print(f"Bibliotecas JSON → {LIBRARIES}")
    print(f"Catálogo V3 → {CATALOG}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nCerrado.")
        httpd.server_close()


if __name__ == "__main__":
    main()
