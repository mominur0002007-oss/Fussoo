package com.fussoo.vr.net;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * OkHttp bridge for NewPipeExtractor.
 *
 * <p>The user agent is the single most important line in this file: YouTube serves
 * different HTML to unknown clients, and the extractor parses that HTML. Pinning a
 * current desktop Firefox UA is what NewPipe itself does for exactly this reason.</p>
 */
public final class YtDownloader extends Downloader {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    private final OkHttpClient client;

    public YtDownloader() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public OkHttpClient client() {
        return client;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        String url = request.url();
        String method = request.httpMethod();
        byte[] body = request.dataToSend();

        RequestBody requestBody = null;
        if (body != null && body.length > 0) {
            requestBody = RequestBody.create(body);
        }

        okhttp3.Request.Builder b = new okhttp3.Request.Builder()
                .url(url)
                .method(method, requestBody)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Accept", "*/*");

        Map<String, java.util.List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, java.util.List<String>> e : headers.entrySet()) {
                b.removeHeader(e.getKey());
                for (String v : e.getValue()) {
                    b.addHeader(e.getKey(), v);
                }
            }
        }

        okhttp3.Response r = client.newCall(b.build()).execute();
        try {
            if (r.code() == 429) {
                throw new ReCaptchaException("YouTube is rate limiting this device (HTTP 429)",
                        url);
            }
            String text = "";
            ResponseBody rb = r.body();
            if (rb != null) {
                String enc = r.header("Content-Encoding", "");
                if (enc != null && enc.toLowerCase().contains("gzip")) {
                    try {
                        GZIPInputStream in = new GZIPInputStream(rb.byteStream());
                        Buffer buffer = new Buffer();
                        buffer.readFrom(in);
                        in.close();
                        text = buffer.readUtf8();
                    } catch (IOException plainAlready) {
                        text = rb.string();
                    }
                } else {
                    text = rb.string();
                }
            }
            return new Response(r.code(), r.message(),
                    r.headers().toMultimap(), text, r.request().url().toString());
        } finally {
            r.close();
        }
    }

    public String fetch(String url) throws IOException {
        okhttp3.Request rq = new okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .build();
        okhttp3.Response r = client.newCall(rq).execute();
        try {
            ResponseBody b = r.body();
            return b == null ? null : b.string();
        } finally {
            r.close();
        }
    }
}
