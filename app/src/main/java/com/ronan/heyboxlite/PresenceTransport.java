package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

/** Device registration transport; never sends official account credentials. */
final class PresenceTransport {
    interface Connections {
        HttpURLConnection open(URL url) throws Exception;
    }

    static final class Result {
        final AccessStatus access;
        final int httpStatus;
        final String errorMessage;
        final String diagnosticCode;

        private Result(AccessStatus access, int httpStatus, String errorMessage,
                       String diagnosticCode) {
            this.access = access;
            this.httpStatus = httpStatus;
            this.errorMessage = errorMessage;
            this.diagnosticCode = diagnosticCode;
        }

        static Result failure(int status, String message, String code) {
            return new Result(null, status, message, code);
        }
    }

    private final SessionStore session;
    private final DeviceAuthorizationStore authorization;
    private final Connections connections;

    PresenceTransport(SessionStore session, DeviceAuthorizationStore authorization) {
        this(session, authorization, url -> (HttpURLConnection) url.openConnection());
    }

    PresenceTransport(SessionStore session, DeviceAuthorizationStore authorization,
                      Connections connections) {
        this.session = session;
        this.authorization = authorization;
        this.connections = connections;
    }

    Result send(String payload) {
        String token = authorization.token();
        Result result = request(payload, token);
        if (result.httpStatus == HttpURLConnection.HTTP_UNAUTHORIZED && !token.isEmpty()) {
            authorization.clear(token);
            return request(payload, authorization.token());
        }
        return result;
    }

    private Result request(String payload, String token) {
        HttpURLConnection connection = null;
        try {
            connection = connections.open(new URL(presenceUrl()));
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            authorization.apply(connection, session, token);
            if (payload != null && !payload.isEmpty()) {
                byte[] bytes = payload.getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return httpFailure(status);
            JSONObject body = new JSONObject(read(connection.getInputStream()));
            if (!body.optBoolean("ok", false)) return invalidResponse();
            String issuedToken = body.optString("deviceToken", "");
            if (!issuedToken.isEmpty()) {
                if (!DeviceAuthorizationStore.validToken(issuedToken)) return invalidResponse();
                try {
                    authorization.save(issuedToken);
                } catch (Exception error) {
                    return Result.failure(status, "无法安全保存设备认证，请稍后重试",
                            "token_storage_failed");
                }
            }
            AccessStatus access = AccessStatus.from(body);
            if (!access.banned && authorization.token().isEmpty()) {
                return Result.failure(status, "未取得设备认证，请稍后重试",
                        "token_unavailable");
            }
            return new Result(access, status, "", "");
        } catch (SocketTimeoutException error) {
            return Result.failure(0, "设备认证连接超时，请稍后重试", "timeout");
        } catch (UnknownHostException error) {
            return Result.failure(0, "无法解析更新服务器地址，请检查网络", "dns_failed");
        } catch (SSLException error) {
            return Result.failure(0, "无法安全连接更新服务器，请检查网络和系统时间", "tls_failed");
        } catch (JSONException error) {
            return invalidResponse();
        } catch (Exception error) {
            return Result.failure(0, "无法连接设备认证服务，请检查网络", "connection_failed");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Result httpFailure(int status) {
        String message;
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            message = "设备认证已失效，测试设备需在后台重新授权";
        } else if (status == 429) {
            message = "设备认证请求过于频繁，请稍后重试";
        } else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
            message = "服务器拒绝设备认证";
        } else if (status >= 500) {
            message = "设备认证服务暂不可用，请稍后重试";
        } else {
            message = "设备认证失败（HTTP " + status + "）";
        }
        return Result.failure(status, message, "http_" + status);
    }

    private static Result invalidResponse() {
        return Result.failure(0, "设备认证响应异常，请稍后重试", "invalid_response");
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                value.append(buffer, 0, count);
                if (value.length() > 16 * 1024) throw new JSONException("Response too large");
            }
        }
        return value.toString();
    }

    private static String presenceUrl() throws Exception {
        URI source = new URI(UpdateChecker.requireTrustedUrl(BuildConfig.UPDATE_API_URL));
        return source.getScheme() + "://" + source.getRawAuthority() + "/api/presence";
    }
}
