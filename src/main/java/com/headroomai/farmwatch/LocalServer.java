package com.headroomai.farmwatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Loopback-only HTTP server exposing GET /state, /accounts, /state/{hash}.
 */
@Slf4j
public class LocalServer
{
    private static final int MAX_PORT_RETRY = 9;

    private final FarmwatchConfig config;
    private final FarmwatchState state;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());
    private HttpServer http;
    private int boundPort = -1;

    public LocalServer(FarmwatchConfig config, FarmwatchState state)
    {
        this.config = config;
        this.state = state;
    }

    public int start() throws IOException
    {
        int basePort = config.httpPort();
        IOException lastErr = null;
        for (int i = 0; i <= MAX_PORT_RETRY; i++)
        {
            int tryPort = basePort + i;
            try
            {
                http = HttpServer.create(new InetSocketAddress("127.0.0.1", tryPort), 0);
                http.setExecutor(Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "farmwatch-http");
                    t.setDaemon(true);
                    return t;
                }));
                http.createContext("/state", this::handleState);
                http.createContext("/accounts", this::handleAccounts);
                http.createContext("/health", this::handleHealth);
                http.createContext("/", this::handleRoot);
                http.start();
                boundPort = tryPort;
                refreshSnapshot(state);
                return tryPort;
            }
            catch (BindException e)
            {
                lastErr = e;
            }
        }
        throw lastErr != null ? lastErr : new IOException("Unable to bind HTTP server");
    }

    public void stop()
    {
        if (http != null)
        {
            http.stop(0);
            http = null;
        }
    }

    public int boundPort()
    {
        return boundPort;
    }

    public void refreshSnapshot(FarmwatchState state)
    {
        byte[] all = JsonCodec.encodeAll(state);
        byte[] accounts = JsonCodec.encodeAccountList(state);
        snapshot.set(new Snapshot(all, accounts, etag(all), etag(accounts)));
    }

    // ---- handlers ----

    private void handleState(HttpExchange ex) throws IOException
    {
        if (!applyCors(ex)) return;
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equals("GET"))    { ex.sendResponseHeaders(405, -1); return; }

        String path = ex.getRequestURI().getPath();
        Snapshot s = snapshot.get();
        if ("/state".equals(path) || "/state/".equals(path))
        {
            serveJson(ex, s.all, s.allEtag);
            return;
        }
        // /state/{hash}
        String hashPart = path.substring("/state/".length());
        for (var acc : state.all())
        {
            if (Long.toUnsignedString(acc.getHash()).equals(hashPart))
            {
                byte[] bytes = JsonCodec.encodeOne(state, acc);
                serveJson(ex, bytes, etag(bytes));
                return;
            }
        }
        ex.sendResponseHeaders(404, -1);
    }

    private void handleAccounts(HttpExchange ex) throws IOException
    {
        if (!applyCors(ex)) return;
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equals("GET"))    { ex.sendResponseHeaders(405, -1); return; }
        Snapshot s = snapshot.get();
        serveJson(ex, s.accounts, s.accountsEtag);
    }

    /**
     * Liveness probe. Deliberately bypasses the CORS allowlist and never
     * reflects an Origin header, so a browser on any origin can detect
     * "plugin is running" and then separately learn from a /state 403 that
     * its origin specifically is blocked. Returns a tiny JSON body with the
     * plugin version so the dashboard can show it in the connection panel.
     */
    private void handleHealth(HttpExchange ex) throws IOException
    {
        if (ex.getRequestMethod().equals("OPTIONS"))
        {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            ex.sendResponseHeaders(204, -1);
            return;
        }
        if (!ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(405, -1); return; }
        byte[] body = ("{\"ok\":true,\"pluginVersion\":\"" + JsonCodec.pluginVersion() + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    /**
     * Catches browsers that navigate directly to http://127.0.0.1:17060/ and
     * returns a small human-readable landing page pointing at the dashboard.
     */
    private void handleRoot(HttpExchange ex) throws IOException
    {
        if (!"/".equals(ex.getRequestURI().getPath()))
        {
            ex.sendResponseHeaders(404, -1);
            return;
        }
        byte[] body = ("<!doctype html><meta charset=utf-8><title>Farmwatch plugin</title>"
            + "<body style=\"font:14px/1.5 system-ui;max-width:40em;margin:3em auto;padding:0 1em\">"
            + "<h1>Farmwatch is running</h1>"
            + "<p>Open the dashboard at "
            + "<a href=\"https://hanamigg.github.io/osrs-farming-tracker/\">hanamigg.github.io/osrs-farming-tracker</a>.</p>"
            + "<p>Endpoints: <code>/state</code>, <code>/accounts</code>, <code>/state/&lt;hash&gt;</code>, <code>/health</code>.</p>"
            + "</body>").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void serveJson(HttpExchange ex, byte[] bytes, String etag) throws IOException
    {
        String reqEtag = ex.getRequestHeaders().getFirst("If-None-Match");
        if (etag != null && etag.equals(reqEtag))
        {
            ex.sendResponseHeaders(304, -1);
            return;
        }
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (etag != null) ex.getResponseHeaders().set("ETag", etag);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody())
        {
            os.write(bytes);
        }
    }

    /** @return false and already-wrote-response if origin is not allowed. */
    private boolean applyCors(HttpExchange ex) throws IOException
    {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        List<String> allowed = parseAllowlist(config.allowedOrigins());
        boolean ok = origin == null || matchOrigin(allowed, origin);
        if (!ok)
        {
            ex.sendResponseHeaders(403, -1);
            return false;
        }
        if (origin != null)
        {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Vary", "Origin");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Max-Age", "600");
        }
        return true;
    }

    private static List<String> parseAllowlist(String csv)
    {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String p : csv.split(","))
        {
            String s = p.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static boolean matchOrigin(List<String> allowed, String origin)
    {
        for (String a : allowed)
        {
            if (a.equals(origin)) return true;
            // simple :* wildcard support, e.g. http://localhost:*
            int star = a.indexOf(":*");
            if (star > 0 && origin.startsWith(a.substring(0, star + 1))) return true;
        }
        return false;
    }

    private static String etag(byte[] bytes)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            StringBuilder sb = new StringBuilder(2 + d.length * 2);
            sb.append('"');
            for (byte b : d) sb.append(String.format("%02x", b));
            sb.append('"');
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            return null;
        }
    }

    private static final class Snapshot
    {
        final byte[] all;
        final byte[] accounts;
        final String allEtag;
        final String accountsEtag;

        Snapshot(byte[] all, byte[] accounts, String allEtag, String accountsEtag)
        {
            this.all = all;
            this.accounts = accounts;
            this.allEtag = allEtag;
            this.accountsEtag = accountsEtag;
        }

        static Snapshot empty()
        {
            byte[] empty = "{\"version\":1,\"accounts\":[]}".getBytes(StandardCharsets.UTF_8);
            byte[] el = "[]".getBytes(StandardCharsets.UTF_8);
            return new Snapshot(empty, el, etag(empty), etag(el));
        }
    }

}
