package com.taobao.weex.devtools.debug;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.text.TextUtils;
import android.util.Log;
import com.taobao.weex.utils.WXLogUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.ws.WebSocket;
import okhttp3.ws.WebSocketCall;
import okhttp3.ws.WebSocketListener;
import okio.Buffer;

class OkHttp3SocketClient extends SocketClient {

    private static final String TAG = "OkHttp3SocketClient";

    public OkHttp3SocketClient(DebugServerProxy proxy) {
        super(proxy);
    }

    protected void connect(String url) {
        if (mSocketClient != null) {
            throw new IllegalStateException("OkHttp3SocketClient is already initialized.");
        }
        try {
            mSocketClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();

            if (!TextUtils.isEmpty(url)) {
                Request.Builder requestBuilder = new Request.Builder().url(url);
                WebSocketCall.create((OkHttpClient)mSocketClient, requestBuilder.build())
                    .enqueue(new WebSocketListenerImpl());
            }
        } catch (Exception e) {
            WXLogUtils.e(TAG, e.getMessage());
        }
    }

    protected void close() {
        if (mWebSocket != null) {
            try {
                ((WebSocket)mWebSocket).close(1000, "End of session");
            } catch (Exception e) {
                WXLogUtils.e(TAG, e.getMessage());
            }
            mWebSocket = null;
            WXLogUtils.w(TAG, "Close websocket connection");
        }
    }

    @Override
    protected void sendProtocolMessage(int requestID, String message) {
        if (mWebSocket == null) {
            return;
        }
        try {
            ((WebSocket)mWebSocket).sendMessage(RequestBody.create(WebSocket.TEXT, message));
        } catch (Exception e) {
            WXLogUtils.e(TAG, e.getMessage());

        }
    }

    private void abort(String message, Throwable cause) {
        Log.w(TAG, "Error occurred, shutting down websocket connection: " + message);
        close();

        // Trigger failure callbacks
        if (mConnectCallback != null) {
            mConnectCallback.onFailure(cause);
            mConnectCallback = null;
        }
    }

    public class WebSocketListenerImpl implements WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            mWebSocket = webSocket;
            if (mConnectCallback != null) {
                mConnectCallback.onSuccess(null);
            }
        }

        @Override
        public void onFailure(IOException e, Response response) {
            abort("Websocket onFailure", e);
        }

        @Override
        public void onMessage(ResponseBody responseBody) throws IOException {
            try {
                mProxy.handleMessage(responseBody.string());
            } catch (Exception e) {
                WXLogUtils.v(TAG, "Unexpected I/O exception processing message: " + e);
            }
        }

        @Override
        public void onPong(Buffer buffer) {

        }

        @Override
        public void onClose(int i, String s) {
            if (mHandlerThread != null && mHandlerThread.isAlive()) {
                mHandler.sendEmptyMessage(CLOSE_WEB_SOCKET);
            }
        }
    }
}
