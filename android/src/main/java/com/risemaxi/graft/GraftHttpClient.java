package com.risemaxi.graft;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.risemaxi.graft.interfaces.DownloadProgressCallback;
import com.risemaxi.graft.interfaces.NonEmptyCallback;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.brotli.BrotliInterceptor;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class GraftHttpClient {

    @NonNull
    private final OkHttpClient okHttpClient;

    public GraftHttpClient(@NonNull GraftConfig config) {
        int httpTimeout = config.getHttpTimeout();

        // Increase max requests per host to allow multiple parallel downloads from the same host
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(30);

        this.okHttpClient = new OkHttpClient.Builder()
            .addInterceptor(BrotliInterceptor.INSTANCE)
            .dispatcher(dispatcher)
            .connectTimeout(httpTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(httpTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(httpTimeout, TimeUnit.MILLISECONDS)
            .build();
    }

    public Call enqueue(@NonNull String url, @NonNull NonEmptyCallback<Response> callback) {
        Request request = new Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build();

        Call call = okHttpClient.newCall(request);
        call.enqueue(
            new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    callback.success(response);
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.error(new Exception(e));
                }
            }
        );
        return call;
    }

    public static void writeResponseBodyToFile(@NonNull ResponseBody body, @NonNull File file, @Nullable DownloadProgressCallback callback)
        throws IOException {
        long contentLength = body.contentLength();
        BufferedSource source = body.source();
        BufferedSink sink = Okio.buffer(Okio.sink(file));
        Buffer sinkBuffer = sink.getBuffer();
        long totalBytesRead = 0;
        int bufferSize = 8 * 1024;
        for (long bytesRead; (bytesRead = source.read(sinkBuffer, bufferSize)) != -1; ) {
            sink.emit();
            totalBytesRead += bytesRead;
            if (callback != null) {
                callback.onProgress(totalBytesRead, contentLength);
            }
        }
        sink.flush();
        sink.close();
        source.close();
    }
}
