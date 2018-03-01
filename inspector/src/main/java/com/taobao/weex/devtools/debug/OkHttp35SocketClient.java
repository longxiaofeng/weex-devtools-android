package com.taobao.weex.devtools.debug;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.text.TextUtils;
import android.util.Log;
import com.taobao.weex.utils.WXLogUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class OkHttp35SocketClient extends SocketClient {

    private static final String TAG = "OkHttp35SocketClient";

    public OkHttp35SocketClient(DebugServerProxy proxy) {
        super(proxy);
    }

    protected void connect(String url) {
        if (mSocketClient != null) {
            throw new IllegalStateException("OkHttp35SocketClient is already initialized.");
        }
        try {
            mSocketClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();

            if (!TextUtils.isEmpty(url)) {
                Request.Builder requestBuilder = new Request.Builder().url(url);
                mWebSocketListener = new WebSocketListenerImpl();
                ((OkHttpClient)mSocketClient).newWebSocket(requestBuilder.build(),
                    (WebSocketListener)mWebSocketListener);
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
        ((WebSocket)mWebSocket).send(message);
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

    public class WebSocketListenerImpl extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            mWebSocket = webSocket;
            if (mConnectCallback != null) {
                mConnectCallback.onSuccess(null);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            abort("Websocket exception", t);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                mProxy.handleMessage(text);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (mHandlerThread != null && mHandlerThread.isAlive()) {
                mHandler.sendEmptyMessage(CLOSE_WEB_SOCKET);
            }
        }
    }
}