package com.qiniu.pili.droid.rtcstreaming.demo.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatActivity;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.pili.pldroid.player.PLNetworkManager;
import com.qiniu.pili.droid.rtcstreaming.demo.R;
import com.qiniu.pili.droid.rtcstreaming.demo.core.StreamUtils;
import com.qiniu.pili.droid.streaming.StreamingProfile;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private EditText mRoomIDEditText;
    private EditText mRoomTokenEditText;
    private ProgressDialog mProgressDialog;
    private String mRoomID = "";
    private String mRtmpUrl = "";
    private String mRoomToken = "";

    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    mRoomIDEditText.setText(mRoomID);
                    break;
                case 200:
                    mRoomTokenEditText.setText(mRoomToken);
                    break;
            }
            super.handleMessage(msg);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRoomIDEditText = (EditText) findViewById(R.id.RoomNameEditView);
        SharedPreferences preferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        String roomName = null;
        preferences.getString("roomName",roomName);
        mRoomIDEditText.setText(mRoomID);

        mRoomTokenEditText = (EditText) findViewById(R.id.RoomTokenEditView);
        mRoomTokenEditText.setText(mRoomToken);
        MultiDex.install(this);

        mProgressDialog = new ProgressDialog(this);

        if (!StreamUtils.isNetworkAvailable())
        {
            Toast.makeText(this,"Newwork Bad!", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SharedPreferences.Editor editor = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE).edit();
        editor.putString("roomName", mRoomIDEditText.getText().toString());
        editor.apply();
    }

    public void onClickRoomID(View v) {

        new Thread(new Runnable() {

			@Override
			public void run() {
				String serverURL = “to do“;
				HttpGet httpRequest = new HttpGet(serverURL);
				HttpResponse httpResponse = null;
				try {
					httpResponse = new DefaultHttpClient().execute(httpRequest);
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (httpResponse.getStatusLine().getStatusCode() == 200){
					try {
						String result = EntityUtils.toString(httpResponse.getEntity());
                        try {
                            JSONObject resJsonObj = new JSONObject(result);
                            mRtmpUrl =  resJsonObj.getString("liveUrl");
                            mRoomID  = resJsonObj.getString("roomName");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        Message message = new Message();
                        message.what = 100;
                        mHandler.sendMessage(message);

						Log.d("Http Get","result:"+result);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
    }

    public void onClickToken(View v) {

        new Thread(new Runnable() {

            @Override
            public void run() {
                mRoomID= mRoomIDEditText.getText().toString();
                String  serverURL = “to do”;
                HttpGet httpRequest = new HttpGet(serverURL);
                HttpResponse httpResponse = null;
                try {
                    httpResponse = new DefaultHttpClient().execute(httpRequest);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (httpResponse.getStatusLine().getStatusCode() == 200){
                    try {
                        String result = EntityUtils.toString(httpResponse.getEntity());
                        try {
                            JSONObject resJsonObj = new JSONObject(result);
                            mRoomToken =  resJsonObj.getString("roomToken");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        Message message = new Message();
                        message.what = 200;
                        mHandler.sendMessage(message);

                        Log.d("Http Get","result:"+result);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void onClickAnchor(View v) {
        jumpToStreamingActivity(StreamUtils.RTC_ROLE_ANCHOR, AnchorActivity.class);
    }

    public void onClickAudience(View v) {
//        mRoomIDEditText.setText(mRoomID);
        final String roomName = mRoomIDEditText.getText().toString();
        if ("".equals(roomName)) {
            showToastTips("请输入房间名称 !");
            return;
        }
        mProgressDialog.setMessage("正在获取播放地址..");
        mProgressDialog.show();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                String playURL = StreamUtils.requestPlayURL(roomName);
                String roomToken = StreamUtils.requestRoomToken(roomName);

                //测试
                roomToken = mRoomToken;
                playURL=mRtmpUrl;
//                if (playURL == null || roomToken == null) {
//                    dismissProgressDialog();
//                    showToastTips("无法获取播放地址或者房间信息 !");
//                    return;
//                }
                dismissProgressDialog();
                Log.d(TAG,"Playback: " + playURL);
                Intent intent = new Intent(MainActivity.this, ViceAnchorActivity.class);
                intent.putExtra("videoPath", playURL);
                intent.putExtra("roomName", roomName);
                intent.putExtra("token",roomToken);
                startActivity(intent);
            }
        });
    }

    private void jumpToStreamingActivity(int role, Class<?> cls) {
//        mRoomIDEditText.setText(mRoomID);
        final String roomName = mRoomIDEditText.getText().toString();
        if ("".equals(roomName)) {
            showToastTips("请输入房间名称 !");
            return;
        }
        Intent intent = new Intent(this, cls);
        intent.putExtra("roomName", roomName);
        intent.putExtra("liveUrl",mRtmpUrl);
        intent.putExtra("token",mRoomToken);
        startActivity(intent);
    }

    private void showToastTips(final String tips) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, tips, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void dismissProgressDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog.dismiss();
            }
        });
    }
}
