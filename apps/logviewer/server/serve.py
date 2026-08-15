#!/usr/bin/env python3
import json
import subprocess
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_REPO = "sankarru/fixed"
HOST, PORT = "127.0.0.1", 8787


def gh(args, repo, timeout=90):
    cmd = ["gh"] + args if args[0] == "api" else ["gh", "--repo", repo] + args
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or proc.stdout.strip())
    return proc.stdout


def json_or(handler, status, obj):
    body = json.dumps(obj).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


class Handler(BaseHTTPRequestHandler):
    server_version = "GHAOTLogs/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("[%s] %s\n" % (self.address_string(), fmt % args))

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        qs = urllib.parse.parse_qs(parsed.query)
        path = parsed.path
        repo = (qs.get("repo") or [DEFAULT_REPO])[0]

        try:
            if path == "/":
                body = (
                    "GH AOT log server. Endpoints:\n"
                    "  /runs                recent workflow runs (JSON)\n"
                    "  /status              health (JSON)\n"
                    "  /log?run=<id>        full logs for a run (text)\n"
                    "  /log?run=<id>&failed=1   failed-job logs only\n"
                    "  /runs?repo=<owner/repo>  scoped to a repo\n"
                ).encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            elif path == "/status":
                try:
                    info = gh(["api", "repos/%s" % repo, "--jq",
                               ".full_name + \" branch=\" + .default_branch"], repo)
                except RuntimeError as e:
                    info = "error: %s" % e
                json_or(self, 200, {"ok": True, "repo": repo,
                                    "gh": subprocess.run(["gh", "--version"],
                                                         capture_output=True,
                                                         text=True).stdout.strip(),
                                    "repo_info": info})
            elif path == "/runs":
                runs = gh(["run", "list", "--limit", "20", "--json",
                           "databaseId,displayTitle,status,conclusion,createdAt,workflowName"],
                          repo)
                json_or(self, 200, {"repo": repo, "runs": json.loads(runs)})
            elif path == "/log":
                run = (qs.get("run") or [""])[0]
                if not run:
                    json_or(self, 400, {"error": "missing run id"})
                    return
                args = ["run", "view", run]
                if (qs.get("failed") or [""])[0] == "1":
                    args.append("--log-failed")
                else:
                    args.append("--log")
                text = gh(args, repo, timeout=120)
                body = text.encode("utf-8", "replace")
                self.send_response(200)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            else:
                json_or(self, 404, {"error": "not found: %s" % path})
        except RuntimeError as e:
            json_or(self, 500, {"error": str(e)})
        except subprocess.TimeoutExpired:
            json_or(self, 504, {"error": "gh timed out"})
        except Exception as e:
            json_or(self, 500, {"error": "%s: %s" % (type(e).__name__, e)})


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else PORT
    srv = ThreadingHTTPServer((HOST, port), Handler)
    print("serving on http://%s:%d (press Ctrl-C to stop)" % (HOST, port))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
