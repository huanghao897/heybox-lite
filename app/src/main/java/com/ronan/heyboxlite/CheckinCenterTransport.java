package com.ronan.heyboxlite;

import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/** Owns the HTTPS boundary used by the check-in service client. */
final class CheckinCenterTransport {
    private final CheckinCenterClient.EventLogger eventLogger;

    CheckinCenterTransport(CheckinCenterClient.EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    <T> T request(CheckinCenterClient.Operation operation, String method, String path,
                  String token, JSONObject body, CheckinCenterClient.Parser<T> parser)
            throws CheckinCenterClient.ApiError {
        HttpsURLConnection connection = null;
        long startedAt = SystemClock.elapsedRealtime();
        try {
            URI uri = requireTrustedUri(CheckinCenterClient.API_BASE + path, true, operation);
            URL url = uri.toURL();
            connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CheckinCenterClient.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMillis(operation));
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            if (!token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                byte[] bytes = body.toString().getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new CheckinCenterClient.ApiError(operation, status,
                        "签到服务拒绝了跳转响应");
            }
            String response = readResponse(connection, status, operation);
            if (status < 200 || status >= 300) {
                throw statusError(operation, status, response);
            }
            JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            return parser.parse(json);
        } catch (CheckinCenterClient.ApiError error) {
            logFailure(error, startedAt);
            throw error;
        } catch (IOException error) {
            CheckinCenterClient.ApiError classified = networkError(operation, error);
            logFailure(classified, startedAt);
            throw classified;
        } catch (JSONException error) {
            CheckinCenterClient.ApiError protocol = CheckinCenterClient.protocolError(operation);
            logFailure(protocol, startedAt);
            throw protocol;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    byte[] requestBytes(CheckinCenterClient.Operation operation, String path, String token)
            throws CheckinCenterClient.ApiError {
        HttpsURLConnection connection = null;
        long startedAt = SystemClock.elapsedRealtime();
        try {
            URI uri = requireTrustedUri(CheckinCenterClient.API_BASE + path, true, operation);
            URL url = uri.toURL();
            connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CheckinCenterClient.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(CheckinCenterClient.STANDARD_READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "image/png");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new CheckinCenterClient.ApiError(operation, status,
                        "签到服务拒绝了跳转响应");
            }
            if (status < 200 || status >= 300) {
                String response = readResponse(connection, status, operation);
                throw statusError(operation, status, response);
            }
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                    .startsWith("image/png")) {
                throw CheckinCenterClient.protocolError(operation);
            }
            return readBytes(connection.getInputStream(), CheckinCenterClient.MAX_QR_BYTES,
                    operation);
        } catch (CheckinCenterClient.ApiError error) {
            logFailure(error, startedAt);
            throw error;
        } catch (IOException error) {
            CheckinCenterClient.ApiError classified = networkError(operation, error);
            logFailure(classified, startedAt);
            throw classified;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static URI requireTrustedPairingUri(String value) throws CheckinCenterClient.ApiError {
        return requireTrustedUri(value, false, CheckinCenterClient.Operation.PAIR_START);
    }

    static boolean isTrustedPairingUri(String value) {
        try {
            requireTrustedPairingUri(value);
            return true;
        } catch (CheckinCenterClient.ApiError ignored) {
            return false;
        }
    }

    static URI requireTrustedUri(String value, boolean api,
                                 CheckinCenterClient.Operation operation)
            throws CheckinCenterClient.ApiError {
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            boolean pathAllowed = api
                    ? path != null && path.startsWith("/checkin/api/lite/")
                    : path != null && (path.equals("/checkin")
                    || path.startsWith(CheckinCenterClient.PAIRING_PATH_PREFIX));
            if (!"https".equals(uri.getScheme())
                    || !CheckinCenterClient.TRUSTED_HOST.equals(uri.getHost())
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getUserInfo() != null || uri.getFragment() != null || !pathAllowed) {
                throw new CheckinCenterClient.ApiError(operation, 0, "签到服务地址不受信任");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new CheckinCenterClient.ApiError(operation, 0, "签到服务地址不受信任");
        }
    }

    static int readTimeoutMillis(CheckinCenterClient.Operation operation) {
        return operation == CheckinCenterClient.Operation.RUN_NOW
                || operation == CheckinCenterClient.Operation.SMS_SEND
                || operation == CheckinCenterClient.Operation.SMS_SUBMIT
                || operation == CheckinCenterClient.Operation.PASSWORD_LOGIN
                ? CheckinCenterClient.SIGNING_READ_TIMEOUT_MS
                : CheckinCenterClient.STANDARD_READ_TIMEOUT_MS;
    }

    static CheckinCenterClient.ApiError statusError(CheckinCenterClient.Operation operation,
                                                    int status, String response) {
        String diagnosticCode = serverErrorCode(response);
        String captchaUri = "";
        int retryAfterSeconds = serverRetryAfterSeconds(response);
        String message;
        switch (status) {
            case 401:
                message = operation == CheckinCenterClient.Operation.PAIR_APPROVE
                        ? "签到服务账号或密码错误"
                        : "签到服务连接已失效，请重新连接";
                break;
            case 402:
                message = "请开通或续费小黑盒签到会员";
                break;
            case 403:
                message = operation == CheckinCenterClient.Operation.PAIR_REGISTER
                        || operation == CheckinCenterClient.Operation.REGISTRATION_EMAIL
                        ? "签到服务当前未开放注册" : "当前操作没有权限";
                break;
            case 404:
                if (operation == CheckinCenterClient.Operation.PAIR_POLL
                        || operation == CheckinCenterClient.Operation.PAIR_APPROVE
                        || operation == CheckinCenterClient.Operation.PAIR_REGISTER
                        || operation == CheckinCenterClient.Operation.REGISTRATION_EMAIL) {
                    message = "配对请求不存在，请重新连接";
                } else if (operation == CheckinCenterClient.Operation.SMS_SEND
                        || operation == CheckinCenterClient.Operation.SMS_SUBMIT
                        || operation == CheckinCenterClient.Operation.PASSWORD_LOGIN) {
                    message = "服务器暂未支持手机号登录，请稍后重试";
                } else if (operation == CheckinCenterClient.Operation.BILLING_STATUS
                        || operation == CheckinCenterClient.Operation.BILLING_QR
                        || operation == CheckinCenterClient.Operation.BILLING_CLAIM) {
                    message = "支付订单不存在";
                } else if (operation == CheckinCenterClient.Operation.BILLING_CATALOG
                        || operation == CheckinCenterClient.Operation.BILLING_HISTORY
                        || operation == CheckinCenterClient.Operation.BILLING_REDEEM) {
                    message = "服务器暂未开放此会员操作";
                } else if (operation == CheckinCenterClient.Operation.LEADERBOARD) {
                    message = "排行榜暂不可用";
                } else if (operation == CheckinCenterClient.Operation.HISTORY) {
                    message = "签到历史记录暂不可用";
                } else {
                    message = "签到任务尚未配置";
                }
                break;
            case 409:
                if ((operation == CheckinCenterClient.Operation.SMS_SEND
                        || operation == CheckinCenterClient.Operation.SMS_SUBMIT
                        || operation == CheckinCenterClient.Operation.PASSWORD_LOGIN)
                        && "captcha_required".equals(diagnosticCode)) {
                    captchaUri = serverCaptchaUri(response);
                    message = captchaUri.isEmpty()
                            ? "小黑盒要求安全验证，但验证页面不可用" : "请完成小黑盒安全验证";
                } else if (operation == CheckinCenterClient.Operation.PAIR_REGISTER) {
                    message = "registration_account_used".equals(diagnosticCode)
                            ? "签到服务账号或邮箱已被注册"
                            : "该签到服务账号已存在，或配对状态已变化";
                } else if (operation == CheckinCenterClient.Operation.BILLING_CREATE) {
                    message = "支付渠道暂不可用，请稍后重试";
                } else if (operation == CheckinCenterClient.Operation.BILLING_REDEEM) {
                    message = "当前服务暂不支持兑换";
                } else {
                    message = "当前操作与服务器状态冲突，请稍后重试";
                }
                break;
            case 410:
                if (operation == CheckinCenterClient.Operation.SMS_SUBMIT) {
                    message = "短信验证码已过期，请重新发送";
                } else if (operation == CheckinCenterClient.Operation.BILLING_QR
                        || operation == CheckinCenterClient.Operation.BILLING_STATUS) {
                    message = "支付码已过期，请重新生成";
                } else {
                    message = "配对已过期，请重新连接";
                }
                break;
            case 413:
                message = "签到资料异常，请更新客户端后重试";
                break;
            case 422:
                if (operation == CheckinCenterClient.Operation.PAIR_APPROVE) {
                    message = "签到服务账号信息无效";
                } else if (operation == CheckinCenterClient.Operation.PAIR_REGISTER) {
                    message = "registration_email_code_invalid".equals(diagnosticCode)
                            ? "邮箱验证码错误或已过期" : "注册信息无效，请检查账号、密码和验证码";
                } else if (operation == CheckinCenterClient.Operation.REGISTRATION_EMAIL) {
                    message = "邮箱地址或配对状态无效";
                } else if (operation == CheckinCenterClient.Operation.SMS_SEND) {
                    message = "手机号无效或发送过于频繁，请稍后重试";
                } else if (operation == CheckinCenterClient.Operation.SMS_SUBMIT) {
                    message = "验证码无效、已过期或登录失败";
                } else if (operation == CheckinCenterClient.Operation.PASSWORD_LOGIN) {
                    message = "手机号或密码错误，登录失败";
                } else if (operation == CheckinCenterClient.Operation.TASK_SETTINGS) {
                    message = "签到时间或随机偏移无效";
                } else if (operation == CheckinCenterClient.Operation.BILLING_CLAIM) {
                    message = "支付订单号格式不正确";
                } else if (operation == CheckinCenterClient.Operation.BILLING_CREATE) {
                    message = "套餐或支付金额无效";
                } else if (operation == CheckinCenterClient.Operation.BILLING_REDEEM) {
                    message = "激活码无效、已过期或已使用";
                } else {
                    message = "签到服务请求无效";
                }
                break;
            case 429:
                message = operation == CheckinCenterClient.Operation.REGISTRATION_EMAIL
                        ? "邮箱验证码发送过于频繁，请稍后重试" : "操作过于频繁，请稍后重试";
                break;
            case 502:
            case 503:
                message = operation == CheckinCenterClient.Operation.REGISTRATION_EMAIL
                        ? "验证邮件暂时无法发送，请稍后重试"
                        : "签到服务暂时不可用，请稍后重试";
                break;
            default:
                message = "签到服务请求失败";
                break;
        }
        return new CheckinCenterClient.ApiError(operation, status, message,
                CheckinCenterClient.ErrorKind.HTTP, diagnosticCode, captchaUri,
                retryAfterSeconds);
    }

    static String serverCaptchaUri(String response) {
        try {
            String uri = new JSONObject(response).optString("verification_uri", "").trim();
            return CheckinCaptchaContract.isTrustedPageUri(uri) ? uri : "";
        } catch (JSONException ignored) {
            return "";
        }
    }

    static String serverErrorCode(String response) {
        try {
            String error = new JSONObject(response).optString("error", "");
            if ("captcha_required".equals(error)) return "captcha_required";
            if ("registration email code is invalid".equals(error)) {
                return "registration_email_code_invalid";
            }
            if ("registration account is already used".equals(error)) {
                return "registration_account_used";
            }
            if ("registration email limit reached".equals(error)) {
                return "registration_email_rate_limited";
            }
        } catch (JSONException ignored) {
        }
        return "";
    }

    static int serverRetryAfterSeconds(String response) {
        try {
            int value = new JSONObject(response).optInt("retry_after", 0);
            return value > 0 && value <= 3_600 ? value : 0;
        } catch (JSONException ignored) {
            return 0;
        }
    }

    private static String readResponse(HttpsURLConnection connection, int status,
                                       CheckinCenterClient.Operation operation)
            throws IOException, CheckinCenterClient.ApiError {
        InputStream input = status >= 200 && status < 400
                ? connection.getInputStream() : connection.getErrorStream();
        if (input == null) return "";
        return new String(readBytes(input, CheckinCenterClient.MAX_RESPONSE_BYTES, operation),
                "UTF-8");
    }

    private static byte[] readBytes(InputStream input, int maxBytes,
                                    CheckinCenterClient.Operation operation)
            throws IOException, CheckinCenterClient.ApiError {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new CheckinCenterClient.ApiError(operation, 0, "签到服务响应异常");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static CheckinCenterClient.ApiError networkError(
            CheckinCenterClient.Operation operation, IOException error) {
        if (error instanceof java.net.SocketTimeoutException) {
            String message;
            if (operation == CheckinCenterClient.Operation.RUN_NOW) {
                message = "签到执行超时，请稍后刷新状态";
            } else if (operation == CheckinCenterClient.Operation.PASSWORD_LOGIN) {
                message = "小黑盒登录超时，请稍后重试";
            } else {
                message = "连接签到服务超时，请检查网络";
            }
            return new CheckinCenterClient.ApiError(operation, 0, message,
                    CheckinCenterClient.ErrorKind.TIMEOUT);
        }
        if (error instanceof SSLException) {
            return new CheckinCenterClient.ApiError(operation, 0,
                    "签到服务证书校验失败，请更新客户端", CheckinCenterClient.ErrorKind.TLS);
        }
        if (error instanceof UnknownHostException || error instanceof ConnectException) {
            return new CheckinCenterClient.ApiError(operation, 0, "无法连接签到服务，请检查网络",
                    CheckinCenterClient.ErrorKind.NETWORK);
        }
        return new CheckinCenterClient.ApiError(operation, 0, "签到服务连接异常，请稍后重试",
                CheckinCenterClient.ErrorKind.NETWORK);
    }

    private void logFailure(CheckinCenterClient.ApiError error, long startedAt) {
        String detail = error.diagnosticCode.isEmpty()
                ? "" : " reason=" + error.diagnosticCode;
        eventLogger.log("checkin request failed operation=" + error.operation.name()
                + " status=" + error.statusCode
                + " category=" + error.kind.name().toLowerCase(Locale.ROOT)
                + detail
                + " elapsedMs=" + Math.max(0L, SystemClock.elapsedRealtime() - startedAt));
    }
}
