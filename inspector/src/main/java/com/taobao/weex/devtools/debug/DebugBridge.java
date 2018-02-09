package com.taobao.weex.devtools.debug;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.taobao.weex.WXEnvironment;
import com.taobao.weex.bridge.WXBridgeManager;
import com.taobao.weex.bridge.WXJSObject;
import com.taobao.weex.bridge.WXParams;
import com.taobao.weex.common.IWXBridge;
import com.taobao.weex.devtools.common.LogUtil;
import com.taobao.weex.devtools.websocket.SimpleSession;
import com.taobao.weex.wson.Wson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by budao on 16/6/25.
 */
public class DebugBridge implements IWXBridge {

  private static final String TAG = "DebugBridge";
  private static volatile DebugBridge sInstance;
  private Object mLock = new Object();
  private WXBridgeManager mJsManager;
  private volatile SimpleSession mSession;

  private DebugBridge() {

  }

  public static DebugBridge getInstance() {
    if (sInstance == null) {
      synchronized (DebugBridge.class) {
        if (sInstance == null) {
          sInstance = new DebugBridge();
        }
      }
    }

    return sInstance;
  }

  public void setSession(SimpleSession session) {
    mSession = session;
  }

  public void setBridgeManager(WXBridgeManager bridgeManager) {
    mJsManager = bridgeManager;
  }

  @Override
  public int initFramework(String framework, WXParams params) {
    while (mSession == null || (mSession != null && !mSession.isOpen())) {
      synchronized (mLock) {
        try {
          Log.v(TAG, "waiting for session now");
          mLock.wait(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    return sendMessage(getInitFrameworkMessage(framework, params));
  }

  @Override
  public int initFrameworkEnv(String s, WXParams wxParams, String s1, boolean b) {
    return initFramework(s, wxParams);
  }

  private String getInitFrameworkMessage(String framework, WXParams params) {
    Map<String, Object> func = new HashMap<>();
    func.put(WXDebugConstants.PARAM_JS_SOURCE, framework);
    if (params != null) {
      Map<String, Object> environmentMap = getEnvironmentMap(params);
      if (environmentMap != null && environmentMap.size() > 0) {
        Map<String, Object> wxEnvironment = new HashMap<>();
        wxEnvironment.put(WXDebugConstants.ENV_WX_ENVIRONMENT, environmentMap);
        func.put(WXDebugConstants.PARAM_INIT_ENV, wxEnvironment);
      }
    }

    Map<String, Object> map = new HashMap<>();
    map.put(WXDebugConstants.METHOD, WXDebugConstants.METHOD_INIT_RUNTIME);
    map.put(WXDebugConstants.PARAMS, func);

    return JSON.toJSONString(map);
  }

  private Map<String, Object> getEnvironmentMap(WXParams params) {
    Map<String, Object> environment = new HashMap<>();
    environment.put(WXDebugConstants.ENV_APP_NAME, params.getAppName());
    environment.put(WXDebugConstants.ENV_APP_VERSION, params.getAppVersion());
    environment.put(WXDebugConstants.ENV_PLATFORM, params.getPlatform());
    environment.put(WXDebugConstants.ENV_OS_VERSION, params.getOsVersion());
    environment.put(WXDebugConstants.ENV_LOG_LEVEL, params.getLogLevel());
    environment.put(WXDebugConstants.ENV_WEEX_VERSION, params.getWeexVersion());
    environment.put(WXDebugConstants.ENV_DEVICE_MODEL, params.getDeviceModel());
    environment.put(WXDebugConstants.ENV_INFO_COLLECT, params.getShouldInfoCollect());
    environment.put(WXDebugConstants.ENV_DEVICE_WIDTH, params.getDeviceWidth());
    environment.put(WXDebugConstants.ENV_DEVICE_HEIGHT, params.getDeviceHeight());
    environment.put("runtime", "devtools");

    environment.putAll(WXEnvironment.getCustomOptions());

    return environment;
  }

  @Override
  public int execJS(String instanceId, String namespace, String function, WXJSObject[] args) {
    ArrayList<Object> array = new ArrayList<>();
    int argsCount = args == null ? 0 : args.length;
    for (int i = 0; i < argsCount; i++) {
      if (args[i].type == WXJSObject.String) {
        array.add(args[i].data);
      } else if (args[i].type == WXJSObject.WSON) {
        byte[] wsonData = (byte[]) args[i].data;
        array.add(Wson.parse(wsonData));
      } else {
        array.add(JSON.parse(args[i].data.toString()));
      }
    }

    Map<String, Object> func = new HashMap<>();
    func.put(WXDebugConstants.METHOD, function);
    func.put(WXDebugConstants.ARGS, array);

    // Log.v(TAG, "callJS: function is " + function + ", args " + array);
    Map<String, Object> map = new HashMap<>();
    map.put(WXDebugConstants.METHOD, WXDebugConstants.METHOD_CALL_JS);
    map.put(WXDebugConstants.PARAMS, func);
    return sendMessage(JSON.toJSONString(map));
  }

  @Override
  public byte[] execJSWithResult(String instanceId, String namespace, String function, WXJSObject[] args) {
    //not need impl in dev-tool @gb
    return null;
//    ArrayList<Object> array = new ArrayList<>();
//    int argsCount = args == null ? 0 : args.length;
//    for (int i = 0; i < argsCount; i++) {
//      if (args[i].type == WXJSObject.String) {
//        array.add(args[i].data);
//      } else if (args[i].type == WXJSObject.WSON) {
//        byte[] wsonData = (byte[]) args[i].data;
//        array.add(Wson.parse(wsonData));
//      } else {
//        array.add(JSON.parse(args[i].data.toString()));
//      }
//    }
//
//    Map<String, Object> params = new HashMap<>();
//    params.put(WXDebugConstants.METHOD, function);
//    params.put(WXDebugConstants.ARGS, array);
//
//    //    // Log.v(TAG, "callJS: function is " + function + ", args " + array);
//    //    Map<String, Object> map = new HashMap<>(2);
//    //    map.put(WXDebugConstants.METHOD, WXDebugConstants.METHOD_CALL_JS_RESULT);
//    //    map.put(WXDebugConstants.PARAMS, params);
//
//    final CountDownLatch latch = new CountDownLatch(1);
//    final Map<String, org.json.JSONObject> responseVal = new HashMap<>(1);
//    final String resultKey = "result";
//    NetworkPeerManager networkPeerManager = NetworkPeerManager.getInstanceOrNull();
//    if (null == networkPeerManager) {
//      Log.e(TAG, "execJSWithResult: null == networkPeerManager !");
//      return null;
//    }
//    networkPeerManager.invokeMethodOnPeers(WXDebugConstants.METHOD_CALL_JS_RESULT, params, new
//        PendingRequestCallback() {
//          @Override
//          public void onResponse(JsonRpcPeer peer, JsonRpcResponse response) {
//            responseVal.put(resultKey, response.result);
//            latch.countDown();
//          }
//        });
//    byte[] data = null;
//    try {
//      latch.await(5, TimeUnit.MILLISECONDS);
//      data = responseVal.get(resultKey).getJSONArray(resultKey).toString().getBytes();
//      if (WXEnvironment.isApkDebugable()) {
//        Log.d(TAG, "execJSWithResult,data :" + data);
//      }
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//    return data;
  }


  @Override
  public int execJSService(String javascript) {
    if (!TextUtils.isEmpty(javascript)) {
      Map<String, Object> params = new HashMap<>();
      params.put(WXDebugConstants.PARAM_JS_SOURCE, javascript);

      Map<String, Object> map = new HashMap<>();
      map.put(WXDebugConstants.METHOD, WXDebugConstants.METHOD_IMPORT_JS);
      map.put(WXDebugConstants.PARAMS, params);
      return sendMessage(JSON.toJSONString(map));
    }
    return 0;
  }

  @Override
  public int callNative(String instanceId, JSONArray tasks, String callback) {
    if (mJsManager != null) {
      return mJsManager.callNative(instanceId, tasks, callback);
    } else {
      return 0;
    }
  }

  @Override
  public int callAddElement(String instanceId, String ref, JSONObject dom, String index, String callback) {
    if (mJsManager != null) {
      return mJsManager.callAddElement(instanceId, ref, dom, index, callback);
    } else {
      return 1;
    }
  }

  @Override
  public void reportJSException(String instanceId, String func, String exception) {
    if (mJsManager != null) {
      mJsManager.reportJSException(instanceId, func, exception);
    }
  }

  @Override
  public Object callNativeModule(String instanceId, String module, String method, byte[] arguments, byte[] options) {
    if (mJsManager != null) {
      JSONArray argArray = JSON.parseArray(new String(arguments));
      return mJsManager.callNativeModule(instanceId, module, method, argArray, options);
    }
    return null;
  }

  @Override
  public void callNativeComponent(String instanceId, String componentRef, String method, byte[] arguments, byte[] options) {
    JSONArray argArray = JSON.parseArray(new String(arguments));
    WXBridgeManager.getInstance().callNativeComponent(instanceId, componentRef, method, argArray, options);
  }

  @Override
  public int callCreateBody(String instanceId, String tasks, String callback) {
    if (mJsManager != null) {
      return mJsManager.callCreateBody(instanceId, tasks, callback);
    }
    return 0;
  }

  @Override
  public int callUpdateFinish(String s, byte[] bytes, String s1) {
    if (mJsManager != null) {
      return mJsManager.callUpdateFinish(s, s1);
    }
    return 0;
  }

  @Override
  public int callCreateFinish(String s, byte[] bytes, String s1) {
    if (mJsManager != null) {
      return mJsManager.callCreateFinish(s, s1);
    }
    return 0;
  }

  @Override
  public int callRefreshFinish(String s, byte[] bytes, String s1) {
    if (mJsManager != null) {
      return mJsManager.callRefreshFinish(s, s1);
    }
    return 0;
  }

  @Override
  public int callUpdateAttrs(String s, String s1, byte[] bytes, String s2) {
    if (mJsManager != null) {
      return mJsManager.callUpdateAttrs(s, s1, new String(bytes), s2);
    }
    return 0;
  }

  @Override
  public int callUpdateStyle(String s, String s1, byte[] bytes, String s2) {
    if (mJsManager != null) {
      return mJsManager.callUpdateStyle(s, s1, new String(bytes), s2);
    }
    return 0;
  }

  @Override
  public int callRemoveElement(String s, String s1, String s2) {
    if (mJsManager != null) {
      return mJsManager.callRemoveElement(s, s1, s2);
    }
    return 0;
  }

  @Override
  public int callMoveElement(String s, String s1, String s2, String s3, String s4) {
    if (mJsManager != null) {
      return mJsManager.callMoveElement(s, s1, s2, s3, s4);
    }
    return 0;
  }

  @Override
  public int callAddEvent(String s, String s1, String s2, String s3) {
    if (mJsManager != null) {
      return mJsManager.callAddEvent(s, s1, s2, s3);
    }
    return 0;
  }

  @Override
  public int callRemoveEvent(String s, String s1, String s2, String s3) {
    if (mJsManager != null) {
      return mJsManager.callRemoveEvent(s, s1, s2, s3);
    }
    return 0;
  }

  public void onConnected() {
    Log.v(TAG, "connect to debug server success");
    synchronized (mLock) {
      mLock.notify();
    }
  }

  public void onDisConnected() {
    Log.w(TAG, "WebSocket disconnected");
    synchronized (mLock) {
      mSession = null;
      mLock.notify();
    }
  }

  private int sendMessage(String message) {
    if (mSession != null && mSession.isOpen()) {
      mSession.sendText(message);
      return 1;
    } else {
      // session error, we need stop debug mode and switch to local runtime
      WXBridgeManager.getInstance().stopRemoteDebug();
      return 0;
    }
  }

  public void sendToRemote(String message) {
    if (mSession != null && mSession.isOpen()) {
      mSession.sendText(message);
    }
  }

  public void post(Runnable runnable) {
    if (mSession != null && mSession.isOpen()) {
      mSession.post(runnable);
    }
  }

  public boolean isSessionActive() {
    return mSession != null && mSession.isOpen();
  }

  @Override
  public void takeHeapSnapshot(String filename) {
    LogUtil.log("warning", "Ignore invoke takeSnapshot: " + filename);
  }

  public int callCreateBody(String instanceId, byte[] tasks, String callback) {
    return callCreateBody(instanceId, new String(tasks), callback);
  }

  @Override
  public void reportServerCrash(String instanceId, String crashFile) {
    LogUtil.e("ServerCrash: instanceId: " + instanceId + ", crashFile: " + crashFile);
  }

}
