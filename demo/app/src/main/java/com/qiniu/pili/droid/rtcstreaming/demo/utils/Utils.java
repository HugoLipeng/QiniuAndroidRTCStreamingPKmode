package com.qiniu.pili.droid.rtcstreaming.demo.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.List;

public class Utils {

    public static final String FIRST_OPEN = "first_open";
    public static final String DESCRIPTOR = "com.umeng.share";

    // 定义第三方平台获取的字段
    public static final String SINA = "sina";
    public static final String QQ = "qzone";
    public static final String WECHAT = "wxsession";
    public static final String DOUBAN = "douban";

    // sina
    public static final String UID = "uid";
    public static final String SCREEN_NAME = "screen_name";
    public static final String PROFILE_IMAGE_URL = "profile_image_url";
    public static final String OPENID = "openid";
    public static final String HEADIMG_URL = "headimgurl";
    public static final String NICKNAME = "nickname";

    // 第三方平台key(这些key，是在各大平台申请的，使用的时候需要替换成你的key）
    public static final String QQZONE_APPID = "100909225";
    public static final String QQZONE_APPKEY = "cbfe6bbc344d684a24bb68da757e97b4";

    public static final String DOUBAN_APPKEY = "01fa2a949474b27c05ee7fbdbd5e1774";
    public static final String DOUBAN_SECRET = "6b547e8edaee2fa5";

    public static final String WEXIN_APPID = "wx91accd9v54410795";
    public static final String WEXIN_APPSECRET = "dbb502q500592b11cd6446239ae10ded";

    public static final String SINA_APP_KEY = "3479651310";
    public static final String SINA_APPSECRET = "dad38f43dae7a94377fa9bff62ad6815";

    public static final String THIRD_LOGIN = "third_login";

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    /**
     * Is the live streaming still available
     * @return is the live streaming is available
     */
    public static boolean isLiveStreamingAvailable() {
        // Todo: Please ask your app server, is the live streaming still available
        return true;
    }

    /**
     * 判断微信客户端是否可用
     * @param context
     * @return
     */
    public static boolean isWeixinAvilible(Context context) {
        final PackageManager packageManager = context.getPackageManager();// 获取packagemanager
        List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);// 获取所有已安装程序的包信息
        if (pinfo != null) {
            for (int i = 0; i < pinfo.size(); i++) {
                String pn = pinfo.get(i).packageName;
                if (pn.equals("com.tencent.mm")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 判断qq是否可用
     *
     * @param context
     * @return
     */
    public static boolean isQQClientAvailable(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
        if (pinfo != null) {
            for (int i = 0; i < pinfo.size(); i++) {
                String pn = pinfo.get(i).packageName;
                if (pn.equals("com.tencent.mobileqq")) {
                    return true;
                }
            }
        }
        return false;
    }
}
