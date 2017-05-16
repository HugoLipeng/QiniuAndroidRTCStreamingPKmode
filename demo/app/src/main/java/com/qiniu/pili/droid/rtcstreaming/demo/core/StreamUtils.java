package com.qiniu.pili.droid.rtcstreaming.demo.core;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class StreamUtils {

    private  static String TAG = "StreamUtils";

    public static final boolean IS_USING_STREAMING_JSON = false;

    // 区分主播和副主播
    public static final int RTC_ROLE_ANCHOR = 0x01;
    public static final int RTC_ROLE_VICE_ANCHOR = 0x02;

    // 用户 ID 可以使用业务上的用户 ID
    // 这里为了演示方便，直接使用了随机值
    public static final String DEFAULT_RTC_USER_ID = UUID.randomUUID().toString();
    public static final String PEER_RTC_USER_ID = “10086”;

    // 业务服务器的地址，需要能提供推流地址、播放地址、RoomToke
    // 这里原本填写的是七牛的测试业务服务器，现在需要改写为客户自己的业务服务器
    // 例如：http://www.xxx.com/api/
    public static final String APP_SERVER_BASE = "";

    // 为了 Demo 的演示方便，建议服务器提供一个获取固定推流地址的链接
    // 传给服务器一个 “房间号” ，由服务器根据 “房间号” 返回一个固定的推流地址
    // 例如：http://www.xxx.com/api/stream/room001
    // 这个 “房间号” 必须是业务服务器事先手动为 “主播” 创建的 “连麦房间号”，不能随意设置
    public static String requestPublishAddress(String roomName) {
        if (IS_USING_STREAMING_JSON) {
            return requestStreamJson(roomName);
        } else {
            return requestStreamURL(roomName);
        }
    }

    // 直接使用 URL 地址进行推流
    private static String requestStreamURL(String roomName) {
        String url = APP_SERVER_BASE + "/stream/url/" + roomName;
        return doRequest("GET", url);
    }

    // 使用 StreamJson 进行推流
    private static String requestStreamJson(String roomName) {
        String url = APP_SERVER_BASE + "/stream/json/" + roomName;
        return doRequest("GET", url);
    }

    // 为了 Demo 的演示方便，建议服务器提供一个获取固定播放地址的链接
    // 传给服务器一个 “房间号” ，由服务器根据 “房间号” 返回一个固定的播放地址，跟上面推流地址“匹配”
    // 例如：http://www.xxx.com/api/play/room001
    // 这个 “房间号” 必须是业务服务器事先手动为 “主播” 创建的 “连麦房间号”，不能随意设置
    public static String requestPlayURL(String roomName) {
        String url = APP_SERVER_BASE + "/play/" + roomName;
        return doRequest("GET", url);
    }

    // 为了 Demo 的演示方便，建议服务器提供一个获取 "RoomToken" 的链接
    // 传给服务器 "用户名" 和 “房间号” ，由服务器根据 "用户名" “房间号” 生成一个 roomToken
    // 客户端再以这个 "用户名"、“房间号”、“roomToken” 去加入房间
    // 例如：http://www.xxx.com/api/room/room001/user/user001/token
    // 这个 “房间号” 必须是业务服务器事先手动为 “主播” 创建的 “连麦房间号”，不能随意设置
    public static String requestRoomToken(String roomName) {
        String url = "http://t.xiehou360.com/LiveWebServer/qiniu_mic.do?action=join&uid=110001&roomName="+roomName;
        return doRequest("GET", url);
    }

    public static String requestRoomID(){
        String url = "";
        return doRequest("GET",url);
    }

    // 发送 HTTP 请求获取相关的地址信息
    private static String doRequest(String method, String url) {
        try {
            HttpURLConnection httpConn = (HttpURLConnection) new URL(url).openConnection();
            httpConn.setRequestMethod(method);
            httpConn.setConnectTimeout(5000);
            httpConn.setReadTimeout(10000);
            int responseCode = httpConn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG,"Http Connet Not OK");
                return null;
            }

            int length = httpConn.getContentLength();
            if (length <= 0) {
                Log.e(TAG,"Http Connet Length <0");
                return null;
            }
            InputStream is = httpConn.getInputStream();
            byte[] data = new byte[length];
            int read = is.read(data);
            is.close();
            if (read <= 0) {
                Log.e(TAG,"Http Response data null");
                return null;
            }
            return new String(data, 0, read);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isNetworkAvailable() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("ping -c 1 114.114.114.114");
            int exitValue = ipProcess.waitFor();
            return (exitValue == 0);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }
}
