package com.ahbe.instatunnel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProfileManager {
    private static final String PREFS = "instatunnel_profiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_SELECTED = "selected_profile";

    private ProfileManager() {}

    public static final class Profile {
        public final String id;
        public final String name;
        public final String type;
        public final String raw;

        Profile(String id, String name, String type, String raw) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.raw = raw;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("type", type)
                    .put("raw", raw);
        }

        static Profile fromJson(JSONObject o) {
            return new Profile(
                    o.optString("id", ""),
                    o.optString("name", "بدون نام"),
                    o.optString("type", "unknown"),
                    o.optString("raw", "")
            );
        }

        public String displayType() {
            switch (type) {
                case "vless": return "VLESS";
                case "vmess": return "VMess";
                case "trojan": return "Trojan";
                case "ss": return "Shadowsocks";
                case "socks": return "SOCKS5";
                case "json": return "Xray JSON";
                default: return type.toUpperCase(Locale.US);
            }
        }
    }

    public static synchronized List<Profile> getProfiles(Context context) {
        List<Profile> out = new ArrayList<>();
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PROFILES, "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Profile p = Profile.fromJson(o);
                if (!p.raw.isEmpty()) out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static synchronized void saveProfiles(Context context, List<Profile> profiles) {
        JSONArray array = new JSONArray();
        for (Profile p : profiles) {
            try { array.put(p.toJson()); } catch (Exception ignored) {}
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PROFILES, array.toString()).apply();
    }

    public static synchronized Profile getSelected(Context context) {
        String id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SELECTED, "");
        if (id == null || id.isEmpty()) return null;
        for (Profile p : getProfiles(context)) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    public static synchronized void select(Context context, String id) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SELECTED, id == null ? "" : id).apply();
    }

    public static synchronized void delete(Context context, String id) {
        List<Profile> profiles = getProfiles(context);
        profiles.removeIf(p -> p.id.equals(id));
        saveProfiles(context, profiles);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (id != null && id.equals(prefs.getString(KEY_SELECTED, ""))) {
            prefs.edit().remove(KEY_SELECTED).apply();
        }
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_PROFILES).remove(KEY_SELECTED).apply();
    }

    public static synchronized int addFromText(Context context, String input) throws Exception {
        List<Profile> parsed = parseProfiles(input);
        if (parsed.isEmpty()) throw new Exception("هیچ کانفیگ پشتیبانی‌شده‌ای پیدا نشد.");

        List<Profile> current = getProfiles(context);
        Set<String> existing = new HashSet<>();
        for (Profile p : current) existing.add(p.raw.trim());

        int added = 0;
        for (Profile p : parsed) {
            if (existing.add(p.raw.trim())) {
                current.add(p);
                added++;
            }
        }
        saveProfiles(context, current);

        if (getSelected(context) == null && !current.isEmpty()) {
            select(context, current.get(0).id);
        }
        return added;
    }

    public static List<Profile> parseProfiles(String input) throws Exception {
        List<Profile> out = new ArrayList<>();
        if (input == null) return out;
        String text = input.trim();
        if (text.isEmpty()) return out;

        if (looksLikeXrayJson(text)) {
            validateRawJson(text);
            out.add(new Profile(makeId(text), jsonName(text), "json", text));
            return out;
        }

        String decoded = maybeDecodeSubscription(text);
        if (!decoded.equals(text)) text = decoded;

        String normalized = text.replace("\r", "\n");
        String[] lines = normalized.split("\n+");
        for (String line : lines) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            Profile profile = parseOne(value);
            if (profile != null) out.add(profile);
        }

        if (out.isEmpty() && lines.length == 1) {
            Profile single = parseOne(text);
            if (single != null) out.add(single);
        }
        return out;
    }

    private static Profile parseOne(String value) throws Exception {
        String lower = value.toLowerCase(Locale.US);
        String type;
        if (lower.startsWith("vless://")) type = "vless";
        else if (lower.startsWith("vmess://")) type = "vmess";
        else if (lower.startsWith("trojan://")) type = "trojan";
        else if (lower.startsWith("ss://")) type = "ss";
        else if (lower.startsWith("socks://") || lower.startsWith("socks5://")) type = "socks";
        else return null;

        String name = extractName(value, type);
        return new Profile(makeId(value), name, type, value);
    }

    private static String extractName(String raw, String type) {
        try {
            if ("vmess".equals(type)) {
                String payload = raw.substring("vmess://".length());
                JSONObject o = new JSONObject(new String(decodeBase64Flex(payload), StandardCharsets.UTF_8));
                String ps = o.optString("ps", "").trim();
                if (!ps.isEmpty()) return ps;
                String add = o.optString("add", "").trim();
                if (!add.isEmpty()) return add;
            }
            Uri uri = Uri.parse(raw);
            String fragment = uri.getFragment();
            if (fragment != null && !fragment.trim().isEmpty()) return fragment.trim();
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) return host;
        } catch (Exception ignored) {
        }
        return type.toUpperCase(Locale.US) + " config";
    }

    private static String jsonName(String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            String remark = root.optString("remarks", "").trim();
            if (!remark.isEmpty()) return remark;
            String name = root.optString("name", "").trim();
            if (!name.isEmpty()) return name;
        } catch (Exception ignored) {}
        return "Xray JSON";
    }

    private static boolean looksLikeXrayJson(String text) {
        String t = text.trim();
        return t.startsWith("{") && t.endsWith("}") && t.contains("\"outbounds\"");
    }

    private static void validateRawJson(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        JSONArray outbounds = root.optJSONArray("outbounds");
        if (outbounds == null || outbounds.length() == 0) {
            throw new Exception("JSON باید کانفیگ Xray/V2Ray و دارای outbounds باشد.");
        }
        JSONObject first = outbounds.optJSONObject(0);
        if (first == null || first.optString("protocol", "").isEmpty()) {
            throw new Exception("این JSON شبیه sing-box است؛ فعلاً JSON کامل Xray/V2Ray پشتیبانی می‌شود.");
        }
    }

    private static String maybeDecodeSubscription(String text) {
        String compact = text.replace("\r", "").replace("\n", "").trim();
        if (compact.contains("://") || compact.startsWith("{")) return text;
        try {
            String decoded = new String(decodeBase64Flex(compact), StandardCharsets.UTF_8).trim();
            if (decoded.contains("vless://") || decoded.contains("vmess://") || decoded.contains("trojan://") || decoded.contains("ss://") || decoded.contains("socks://")) {
                return decoded;
            }
        } catch (Exception ignored) {}
        return text;
    }

    private static byte[] decodeBase64Flex(String value) {
        String s = value.trim();
        int mod = s.length() % 4;
        if (mod != 0) s = s + "====".substring(mod);
        try {
            return Base64.decode(s, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception e) {
            return Base64.decode(s, Base64.DEFAULT);
        }
    }

    private static String makeId(String raw) {
        return Integer.toHexString(raw.trim().hashCode()) + "-" + Integer.toHexString((raw.trim() + "insta").hashCode());
    }

    public static String buildXrayConfig(Profile profile, int localSocksPort) throws Exception {
        if (profile == null) throw new Exception("کانفیگی انتخاب نشده است.");

        JSONObject inbound = new JSONObject()
                .put("listen", "127.0.0.1")
                .put("port", localSocksPort)
                .put("protocol", "socks")
                .put("settings", new JSONObject().put("auth", "noauth").put("udp", true));

        if ("json".equals(profile.type)) {
            JSONObject root = new JSONObject(profile.raw);
            validateRawJson(profile.raw);
            root.put("inbounds", new JSONArray().put(inbound));
            if (!root.has("log")) root.put("log", new JSONObject().put("loglevel", "warning"));
            return root.toString();
        }

        JSONObject outbound;
        switch (profile.type) {
            case "vless": outbound = buildVless(profile.raw); break;
            case "vmess": outbound = buildVmess(profile.raw); break;
            case "trojan": outbound = buildTrojan(profile.raw); break;
            case "ss": outbound = buildShadowsocks(profile.raw); break;
            case "socks": outbound = buildSocks(profile.raw); break;
            default: throw new Exception("نوع کانفیگ پشتیبانی نمی‌شود: " + profile.type);
        }
        outbound.put("tag", "proxy");

        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("loglevel", "warning"));
        root.put("inbounds", new JSONArray().put(inbound));
        root.put("outbounds", new JSONArray()
                .put(outbound)
                .put(new JSONObject().put("protocol", "freedom").put("tag", "direct"))
                .put(new JSONObject().put("protocol", "blackhole").put("tag", "block")));
        return root.toString();
    }

    private static JSONObject buildVless(String raw) throws Exception {
        Uri uri = Uri.parse(raw);
        String id = userInfo(uri);
        String host = requireHost(uri);
        int port = requirePort(uri, 443);
        if (id.isEmpty()) throw new Exception("UUID در کانفیگ VLESS خالی است.");

        JSONObject user = new JSONObject().put("id", id).put("encryption", value(uri, "encryption", "none"));
        String flow = value(uri, "flow", "");
        if (!flow.isEmpty()) user.put("flow", flow);

        JSONObject outbound = new JSONObject()
                .put("protocol", "vless")
                .put("settings", new JSONObject().put("vnext", new JSONArray().put(
                        new JSONObject().put("address", host).put("port", port)
                                .put("users", new JSONArray().put(user))
                )));
        applyStreamSettings(outbound, uri);
        return outbound;
    }

    private static JSONObject buildTrojan(String raw) throws Exception {
        Uri uri = Uri.parse(raw);
        String password = userInfo(uri);
        String host = requireHost(uri);
        int port = requirePort(uri, 443);
        if (password.isEmpty()) throw new Exception("رمز Trojan خالی است.");

        JSONObject outbound = new JSONObject()
                .put("protocol", "trojan")
                .put("settings", new JSONObject().put("servers", new JSONArray().put(
                        new JSONObject().put("address", host).put("port", port).put("password", password)
                )));
        applyStreamSettings(outbound, uri);
        return outbound;
    }

    private static JSONObject buildVmess(String raw) throws Exception {
        String payload = raw.substring("vmess://".length()).trim();
        JSONObject v = new JSONObject(new String(decodeBase64Flex(payload), StandardCharsets.UTF_8));
        String host = v.optString("add", "").trim();
        int port = parseInt(v.optString("port", "443"), 443);
        String id = v.optString("id", "").trim();
        if (host.isEmpty() || id.isEmpty()) throw new Exception("کانفیگ VMess ناقص است.");

        JSONObject user = new JSONObject().put("id", id)
                .put("alterId", parseInt(v.optString("aid", "0"), 0))
                .put("security", v.optString("scy", "auto").isEmpty() ? "auto" : v.optString("scy", "auto"));

        JSONObject outbound = new JSONObject()
                .put("protocol", "vmess")
                .put("settings", new JSONObject().put("vnext", new JSONArray().put(
                        new JSONObject().put("address", host).put("port", port)
                                .put("users", new JSONArray().put(user))
                )));

        JSONObject stream = new JSONObject();
        String network = normalizeNetwork(v.optString("net", "tcp"));
        stream.put("network", network);
        String security = v.optString("tls", "").trim();
        if (!security.isEmpty() && !"none".equalsIgnoreCase(security)) {
            stream.put("security", security);
            JSONObject tls = new JSONObject();
            String sni = v.optString("sni", "").trim();
            if (!sni.isEmpty()) tls.put("serverName", sni);
            String fp = v.optString("fp", "").trim();
            if (!fp.isEmpty()) tls.put("fingerprint", fp);
            stream.put("tlsSettings", tls);
        }
        applyTransport(stream, network, v.optString("path", ""), v.optString("host", ""), v.optString("type", ""));
        outbound.put("streamSettings", stream);
        return outbound;
    }

    private static JSONObject buildShadowsocks(String raw) throws Exception {
        String value = raw.substring("ss://".length());
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        int q = value.indexOf('?');
        if (q >= 0) {
            String query = value.substring(q + 1);
            if (query.toLowerCase(Locale.US).contains("plugin=")) {
                throw new Exception("Shadowsocks دارای plugin فعلاً پشتیبانی نمی‌شود.");
            }
            value = value.substring(0, q);
        }

        String credentials;
        String hostPort;
        int at = value.lastIndexOf('@');
        if (at >= 0) {
            credentials = value.substring(0, at);
            hostPort = value.substring(at + 1);
            if (!credentials.contains(":")) credentials = new String(decodeBase64Flex(credentials), StandardCharsets.UTF_8);
        } else {
            String decoded = new String(decodeBase64Flex(value), StandardCharsets.UTF_8);
            int decodedAt = decoded.lastIndexOf('@');
            if (decodedAt < 0) throw new Exception("ساختار Shadowsocks نامعتبر است.");
            credentials = decoded.substring(0, decodedAt);
            hostPort = decoded.substring(decodedAt + 1);
        }

        int colon = credentials.indexOf(':');
        if (colon <= 0) throw new Exception("method/password در Shadowsocks نامعتبر است.");
        String method = credentials.substring(0, colon);
        String password = credentials.substring(colon + 1);
        HostPort hp = parseHostPort(hostPort, 443);

        return new JSONObject()
                .put("protocol", "shadowsocks")
                .put("settings", new JSONObject().put("servers", new JSONArray().put(
                        new JSONObject().put("address", hp.host).put("port", hp.port)
                                .put("method", method).put("password", password)
                )));
    }

    private static JSONObject buildSocks(String raw) throws Exception {
        Uri uri = Uri.parse(raw.replaceFirst("(?i)^socks5://", "socks://"));
        String host = requireHost(uri);
        int port = requirePort(uri, 1080);
        JSONObject server = new JSONObject().put("address", host).put("port", port);
        String info = uri.getUserInfo();
        if (info != null && !info.isEmpty()) {
            String[] parts = info.split(":", 2);
            JSONObject user = new JSONObject().put("user", Uri.decode(parts[0]));
            if (parts.length > 1) user.put("pass", Uri.decode(parts[1]));
            server.put("users", new JSONArray().put(user));
        }
        return new JSONObject().put("protocol", "socks")
                .put("settings", new JSONObject().put("servers", new JSONArray().put(server)));
    }

    private static void applyStreamSettings(JSONObject outbound, Uri uri) throws Exception {
        JSONObject stream = new JSONObject();
        String network = normalizeNetwork(value(uri, "type", "tcp"));
        stream.put("network", network);

        String security = value(uri, "security", "");
        if (!security.isEmpty() && !"none".equalsIgnoreCase(security)) {
            stream.put("security", security);
            if ("reality".equalsIgnoreCase(security)) {
                JSONObject reality = new JSONObject();
                putIfNotEmpty(reality, "serverName", value(uri, "sni", ""));
                putIfNotEmpty(reality, "fingerprint", value(uri, "fp", "chrome"));
                putIfNotEmpty(reality, "publicKey", value(uri, "pbk", ""));
                putIfNotEmpty(reality, "shortId", value(uri, "sid", ""));
                putIfNotEmpty(reality, "spiderX", value(uri, "spx", ""));
                stream.put("realitySettings", reality);
            } else if ("tls".equalsIgnoreCase(security)) {
                JSONObject tls = new JSONObject();
                putIfNotEmpty(tls, "serverName", value(uri, "sni", value(uri, "host", "")));
                putIfNotEmpty(tls, "fingerprint", value(uri, "fp", ""));
                String alpn = value(uri, "alpn", "");
                if (!alpn.isEmpty()) tls.put("alpn", new JSONArray(alpn.contains(",") ? "[\"" + alpn.replace(",", "\",\"") + "\"]" : "[\"" + alpn + "\"]"));
                stream.put("tlsSettings", tls);
            }
        }

        applyTransport(stream, network, value(uri, "path", ""), value(uri, "host", ""), value(uri, "headerType", ""));
        outbound.put("streamSettings", stream);
    }

    private static void applyTransport(JSONObject stream, String network, String path, String host, String headerType) throws Exception {
        if ("ws".equals(network)) {
            JSONObject ws = new JSONObject();
            if (!path.isEmpty()) ws.put("path", path);
            if (!host.isEmpty()) ws.put("headers", new JSONObject().put("Host", host));
            stream.put("wsSettings", ws);
        } else if ("grpc".equals(network)) {
            JSONObject grpc = new JSONObject();
            if (!path.isEmpty()) grpc.put("serviceName", path.startsWith("/") ? path.substring(1) : path);
            stream.put("grpcSettings", grpc);
        } else if ("httpupgrade".equals(network)) {
            JSONObject hu = new JSONObject();
            if (!path.isEmpty()) hu.put("path", path);
            if (!host.isEmpty()) hu.put("host", host);
            stream.put("httpupgradeSettings", hu);
        } else if ("tcp".equals(network) && !headerType.isEmpty() && !"none".equalsIgnoreCase(headerType)) {
            stream.put("tcpSettings", new JSONObject().put("header", new JSONObject().put("type", headerType)));
        }
    }

    private static String normalizeNetwork(String network) {
        String n = network == null ? "tcp" : network.trim().toLowerCase(Locale.US);
        if (n.isEmpty()) return "tcp";
        if ("h2".equals(n)) return "http";
        if ("httpupgrade".equals(n) || "ws".equals(n) || "grpc".equals(n) || "tcp".equals(n) || "kcp".equals(n) || "http".equals(n) || "xhttp".equals(n)) return n;
        return n;
    }

    private static String userInfo(Uri uri) {
        String info = uri.getUserInfo();
        if (info == null) return "";
        int colon = info.indexOf(':');
        return Uri.decode(colon >= 0 ? info.substring(0, colon) : info).trim();
    }

    private static String requireHost(Uri uri) throws Exception {
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) throw new Exception("آدرس سرور در کانفیگ خالی است.");
        return host.trim();
    }

    private static int requirePort(Uri uri, int fallback) throws Exception {
        int port = uri.getPort();
        if (port <= 0) port = fallback;
        if (port <= 0 || port > 65535) throw new Exception("پورت کانفیگ نامعتبر است.");
        return port;
    }

    private static String value(Uri uri, String key, String fallback) {
        String v = uri.getQueryParameter(key);
        return v == null ? fallback : v.trim();
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private static void putIfNotEmpty(JSONObject o, String key, String value) throws Exception {
        if (value != null && !value.trim().isEmpty()) o.put(key, value.trim());
    }

    private static HostPort parseHostPort(String value, int fallbackPort) throws Exception {
        String v = value.trim();
        if (v.startsWith("[")) {
            int close = v.indexOf(']');
            if (close < 0) throw new Exception("IPv6 نامعتبر است.");
            String host = v.substring(1, close);
            int port = fallbackPort;
            if (close + 2 <= v.length() && close + 1 < v.length() && v.charAt(close + 1) == ':') {
                port = parseInt(v.substring(close + 2), fallbackPort);
            }
            return new HostPort(host, port);
        }
        int colon = v.lastIndexOf(':');
        if (colon <= 0) return new HostPort(v, fallbackPort);
        return new HostPort(v.substring(0, colon), parseInt(v.substring(colon + 1), fallbackPort));
    }

    private static final class HostPort {
        final String host;
        final int port;
        HostPort(String host, int port) { this.host = host; this.port = port; }
    }
}
