package com.taobao.weex.devtools.debug;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import android.text.TextUtils;
import android.util.Log;
import com.taobao.weex.devtools.common.ReflectionUtil;
import com.taobao.weex.utils.WXLogUtils;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class OkHttp35SocketClient extends SocketClient {

    private static final String TAG = "OkHttp35SocketClient";

    private static HashMap<String, Class> sClazzMap = new HashMap<String, Class>();
    private static final String CLASS_WEBSOCKET = "okhttp3.WebSocket";
    private static final String CLASS_WEBSOCKET_LISTENER = "okhttp3.WebSocketListener";

    private static final String CLASS_OKHTTP_CLIENT = "okhttp3.OkHttpClient";
    private static final String CLASS_OKHTTP_CLIENT_BUILDER = "okhttp3.OkHttpClient$Builder";
    private static final String CLASS_RESPONSE = "okhttp3.Response";
    private static final String CLASS_REQUEST = "okhttp3.Request";
    private static final String CLASS_REQUEST_BUILDER = "okhttp3.Request$Builder";

    static {
        String[] classNames = new String[] {
            CLASS_WEBSOCKET,
            CLASS_WEBSOCKET_LISTENER,
            CLASS_OKHTTP_CLIENT,
            CLASS_OKHTTP_CLIENT_BUILDER,
            CLASS_RESPONSE,
            CLASS_REQUEST,
            CLASS_REQUEST_BUILDER
        };
        for (String className : classNames) {
            sClazzMap.put(className, ReflectionUtil.tryGetClassForName(className));
        }
    }

    private Class mOkHttpClientClazz = sClazzMap.get(CLASS_OKHTTP_CLIENT);
    private Class mOkHttpClientBuilderClazz = sClazzMap.get(CLASS_OKHTTP_CLIENT_BUILDER);

    private Class mRequestClazz = sClazzMap.get(CLASS_REQUEST);
    private Class mRequestBuilderClazz = sClazzMap.get(CLASS_REQUEST_BUILDER);
    private Class mWebSocketListenerClazz = sClazzMap.get(CLASS_WEBSOCKET_LISTENER);

    private Class mWebSocketClazz = sClazzMap.get(CLASS_WEBSOCKET);

    public OkHttp35SocketClient(DebugServerProxy proxy) {
        super(proxy);
    }

    protected void connect(String url) {
        if (mSocketClient != null) {
            throw new IllegalStateException("OkHttp3SocketClient is already initialized.");
        }
        try {
            Object builder = mOkHttpClientBuilderClazz.newInstance();
            Method connectTimeout = ReflectionUtil.tryGetMethod(
                mOkHttpClientBuilderClazz,
                "connectTimeout",
                new Class[] {long.class, TimeUnit.class});

            Method writeTimeout = ReflectionUtil.tryGetMethod(
                mOkHttpClientBuilderClazz,
                "writeTimeout",
                new Class[] {long.class, TimeUnit.class});

            Method readTimeout = ReflectionUtil.tryGetMethod(
                mOkHttpClientBuilderClazz,
                "readTimeout",
                new Class[] {long.class, TimeUnit.class});

            builder = ReflectionUtil.tryInvokeMethod(builder, connectTimeout, 30, TimeUnit.SECONDS);
            builder = ReflectionUtil.tryInvokeMethod(builder, writeTimeout, 30, TimeUnit.SECONDS);
            builder = ReflectionUtil.tryInvokeMethod(builder, readTimeout, 0, TimeUnit.SECONDS);

            Method build = ReflectionUtil.tryGetMethod(mOkHttpClientBuilderClazz, "build");

            mSocketClient = ReflectionUtil.tryInvokeMethod(builder, build);

            if (!TextUtils.isEmpty(url)) {
                Object requestBuilder = mRequestBuilderClazz.newInstance();
                Method urlMethod = ReflectionUtil.tryGetMethod(
                    mRequestBuilderClazz,
                    "url",
                    new Class[] {String.class});
                Method buildMethod = ReflectionUtil.tryGetMethod(
                    mRequestBuilderClazz,
                    "build");

                requestBuilder = ReflectionUtil.tryInvokeMethod(requestBuilder, urlMethod, url);
                Object request = ReflectionUtil.tryInvokeMethod(requestBuilder, buildMethod);

                Method newWebSocket = ReflectionUtil.tryGetMethod(
                    mOkHttpClientClazz,
                    "newWebSocket",
                    new Class[] {mRequestClazz, mWebSocketListenerClazz});

                mWebSocketListener = new WebSocketListenerIml();
                ReflectionUtil.tryInvokeMethod(mSocketClient, newWebSocket, request, mWebSocketListener);
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    protected void close() {
        if (mWebSocket != null) {
            Method closeMethod = ReflectionUtil.tryGetMethod(
                mWebSocketClazz,
                "close",
                new Class[] {int.class, String.class});

            ReflectionUtil.tryInvokeMethod(mWebSocket, closeMethod, 1000, "End of session");
            mWebSocket = null;
            WXLogUtils.w(TAG, "Close websocket connection");
        }
    }

    @Override
    protected void sendProtocolMessage(int requestID, String message) {
        if (mWebSocket == null) {
            return;
        }
        Method sendMethod = ReflectionUtil.tryGetMethod(
            mWebSocketClazz,
            "send",
            new Class[] {String.class});

        ReflectionUtil.tryInvokeMethod(mWebSocket, sendMethod, message);
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

    public class WebSocketListenerIml extends WebSocketListener {

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