package com.qiniu.pili.droid.rtcstreaming.demo.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qiniu.pili.droid.rtcstreaming.RTCConferenceOptions;
import com.qiniu.pili.droid.rtcstreaming.RTCConferenceState;
import com.qiniu.pili.droid.rtcstreaming.RTCConferenceStateChangedListener;
import com.qiniu.pili.droid.rtcstreaming.RTCMediaStreamingManager;
import com.qiniu.pili.droid.rtcstreaming.RTCRemoteWindowEventListener;
import com.qiniu.pili.droid.rtcstreaming.RTCStartConferenceCallback;
import com.qiniu.pili.droid.rtcstreaming.RTCUserEventListener;
import com.qiniu.pili.droid.rtcstreaming.RTCVideoWindow;
import com.qiniu.pili.droid.rtcstreaming.demo.R;
import com.qiniu.pili.droid.rtcstreaming.demo.core.StreamUtils;
import com.qiniu.pili.droid.streaming.CameraStreamingSetting;
import com.qiniu.pili.droid.streaming.StreamStatusCallback;
import com.qiniu.pili.droid.streaming.StreamingProfile;
import com.qiniu.pili.droid.streaming.StreamingSessionListener;
import com.qiniu.pili.droid.streaming.StreamingState;
import com.qiniu.pili.droid.streaming.StreamingStateChangedListener;
import com.qiniu.pili.droid.streaming.WatermarkSetting;
import com.qiniu.pili.droid.streaming.widget.AspectFrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.os.Environment;

import com.qiniu.pili.droid.streaming.AudioSourceCallback;


/**
 *  演示使用 SDK 内部的 Video/Audio 采集，实现连麦 & 推流
 */
public class LiveActivity extends AppCompatActivity implements AudioSourceCallback{
    private static final String TAG = "RTCMediaStreaming";

    private TextView mStatusTextView;
    private TextView mStatTextView;
    private Button   mControlButton;
    private CheckBox mMuteCheckBox;
    private CheckBox mConferenceCheckBox;

    private ProgressDialog mProgressDialog;

    private RTCMediaStreamingManager mRTCStreamingManager;

    private StreamingProfile mStreamingProfile;

    private boolean mIsActivityPaused = true;
    private boolean mIsPublishStreamStarted = false;
    private boolean mIsConferenceStarted = false;
    private boolean mIsInReadyState = false;
    private int     mCurrentCamFacingIndex;

    private String mRoomName;
    private String mLiveUrl;
    private String mToken;
    private String mRoomId;

    private FrameLayout mPeerfl;
    private GLSurfaceView mPeerView;

    private GLSurfaceView mCameraView;

    private AspectFrameLayout mAfl;

    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_anchor_streaming);

        RTCMediaStreamingManager.init(getApplicationContext());

        //获取屏幕宽高
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        mScreenWidth = wm.getDefaultDisplay().getWidth();
        mScreenHeight = wm.getDefaultDisplay().getHeight();


        mAfl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
//        mAfl.setAspectRatio(0.8);
//        mAfl.setShowMode(AspectFrameLayout.SHOW_MODE.REAL);
        mAfl.setShowMode(AspectFrameLayout.SHOW_MODE.FULL);
        mCameraView =(GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);


        mLiveUrl = getIntent().getStringExtra("liveUrl");
        mRoomId  = getIntent().getStringExtra("roomId");
        mRoomName  = getIntent().getStringExtra("roomName");
        mToken = getIntent().getStringExtra("token");

        mControlButton = (Button) findViewById(R.id.ControlButton);
        mStatusTextView = (TextView) findViewById(R.id.StatusTextView);
        mStatTextView = (TextView) findViewById(R.id.StatTextView);
        mMuteCheckBox = (CheckBox) findViewById(R.id.MuteCheckBox);
        mMuteCheckBox.setOnClickListener(mMuteButtonClickListener);
        mConferenceCheckBox = (CheckBox) findViewById(R.id.ConferenceCheckBox);
        mConferenceCheckBox.setOnClickListener(mConferenceButtonClickListener);
        mConferenceCheckBox.setVisibility(View.VISIBLE);


        CameraStreamingSetting.CAMERA_FACING_ID facingId = chooseCameraFacingId();
        mCurrentCamFacingIndex = facingId.ordinal();

        // Config the inner camera settings
        CameraStreamingSetting cameraStreamingSetting = new CameraStreamingSetting();
        cameraStreamingSetting.setCameraFacingId(facingId)
                .setContinuousFocusModeEnabled(true)
                .setRecordingHint(false)
                .setResetTouchFocusDelayInMs(3000)
                .setFocusMode(CameraStreamingSetting.FOCUS_MODE_CONTINUOUS_PICTURE)
                .setCameraPrvSizeLevel(CameraStreamingSetting.PREVIEW_SIZE_LEVEL.MEDIUM)
                .setCameraPrvSizeRatio(CameraStreamingSetting.PREVIEW_SIZE_RATIO.RATIO_16_9)
                .setBuiltInFaceBeautyEnabled(true) // Using sdk built in face beauty algorithm
                .setFaceBeautySetting(new CameraStreamingSetting.FaceBeautySetting(0.8f, 0.8f, 0.6f)) // sdk built in face beauty settings
                .setVideoFilter(CameraStreamingSetting.VIDEO_FILTER_TYPE.VIDEO_FILTER_BEAUTY); // set the beauty on/off

        mRTCStreamingManager = new RTCMediaStreamingManager(getApplicationContext(),  mCameraView);
        mRTCStreamingManager.setConferenceStateListener(mRTCStreamingStateChangedListener);
        mRTCStreamingManager.setRemoteWindowEventListener(mRTCRemoteWindowEventListener);
        mRTCStreamingManager.setUserEventListener(mRTCUserEventListener);
        mRTCStreamingManager.setDebugLoggingEnabled(true);

        mRTCStreamingManager.setAudioSourceCallback(this);

        RTCConferenceOptions options = new RTCConferenceOptions();

        // anchor should use a bigger size, must equals to `StreamProfile.setPreferredVideoEncodingSize` or `StreamProfile.setEncodingSizeLevel`
        // RATIO_16_9 & VIDEO_ENCODING_SIZE_HEIGHT_480 means the output size is 640 x 480
        options.setVideoEncodingSizeRatio(RTCConferenceOptions.VIDEO_ENCODING_SIZE_RATIO.RATIO_16_9);
        options.setVideoEncodingSizeLevel(RTCConferenceOptions.VIDEO_ENCODING_SIZE_HEIGHT_480);
        // anchor can use a smaller conference bitrate in order to reserve enough bandwidth for rtmp streaming
        options.setVideoBitrateRange(500 * 1000, 800 * 1000);
        // 24 fps is enough
        options.setVideoEncodingFps(15);

        mRTCStreamingManager.setConferenceOptions(options);

        // add the remote window
        mPeerfl = (FrameLayout) findViewById(R.id.RemoteWindowA);
        mPeerView = (GLSurfaceView)findViewById(R.id.RemoteGLSurfaceViewA);
        RTCVideoWindow windowA = new RTCVideoWindow(mPeerfl, mPeerView);

        // The anchor must configure the mix stream position and size
        // set mix overlay params with absolute value
        // the w & h of remote window equals with or smaller than the vice anchor can reduce cpu consumption
        mRTCStreamingManager.setLocalWindowPosition(new RectF(0, 0, 0.5f, 0.5f));//
        //mRTCStreamingManager.setLocalWindowPosition(new RectF(0, 0.25f, 0.5f, 0.75f));/
//         windowA.setAbsolutetMixOverlayRect(240, 100, 240, 320);
        windowA.setRelativeMixOverlayRect(0.5f, 0.0f, 0.5f, 0.5f);
        //windowA.setRelativeMixOverlayRect(0.5f, 0.25f, 0.5f, 0.5f);
        // set mix overlay params with relative value
        // windowA.setRelativeMixOverlayRect(0.65f, 0.2f, 0.3f, 0.3f);

        /*setLocalWindowPosition(new RectF(0, 0, 0.5f, 0.5f));
        setRelativeMixOverlayRect(0.5f, 0.0f, 0.5f, 0.5f);
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        */

        mRTCStreamingManager.addRemoteWindow(windowA);

        // the anchor must configure the `StreamingProfile`
        mRTCStreamingManager.setStreamStatusCallback(mStreamStatusCallback);
        mRTCStreamingManager.setStreamingStateListener(mStreamingStateChangedListener);
        mRTCStreamingManager.setStreamingSessionListener(mStreamingSessionListener);

        mStreamingProfile = new StreamingProfile();
        mStreamingProfile.setVideoQuality(StreamingProfile.VIDEO_QUALITY_MEDIUM2)
                .setAudioQuality(StreamingProfile.AUDIO_QUALITY_MEDIUM1)
                .setEncoderRCMode(StreamingProfile.EncoderRCModes.QUALITY_PRIORITY)
                .setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.PORT)
//                    .setPreferredVideoEncodingSize(options.getVideoEncodingWidth()   , options.getVideoEncodingHeight());
                .setPreferredVideoEncodingSize(360, 640);

        WatermarkSetting watermarksetting = null;
//            watermarksetting = new WatermarkSetting(this);
//            watermarksetting.setResourceId(R.drawable.qiniu_logo)
//                    .setSize(WatermarkSetting.WATERMARK_SIZE.MEDIUM)
//                    .setAlpha(100)
//                    .setCustomPosition(0.5f, 0.5f);

        mRTCStreamingManager.prepare(cameraStreamingSetting, null,  watermarksetting, mStreamingProfile);

        mProgressDialog = new ProgressDialog(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsActivityPaused = false;
        mRTCStreamingManager.startCapture();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsActivityPaused = true;
        stopConference();
        mRTCStreamingManager.stopCapture();
        stopPublishStreaming();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRTCStreamingManager.destroy();
        RTCMediaStreamingManager.deinit();
    }

//    public void onClickKickoutUserA(View v) {
//        mRTCStreamingManager.kickoutUser(R.id.RemoteGLSurfaceViewA);
//    }

    public void onClickSwitchCamera(View v) {
        mCurrentCamFacingIndex = (mCurrentCamFacingIndex + 1) % CameraStreamingSetting.getNumberOfCameras();
        CameraStreamingSetting.CAMERA_FACING_ID facingId;
        if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK.ordinal()) {
            facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
        } else if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT.ordinal()) {
            facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
        } else {
            facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
        }
        Log.i(TAG, "switchCamera:" + facingId);
        mRTCStreamingManager.switchCamera(facingId);
    }

    public void onClickExit(View v) {
        finish();
    }

    private boolean startConference() {
        //改变布局
        ChangeLayout();

        if (mIsConferenceStarted) {
            return true;
        }

        mProgressDialog.setMessage("正在加入连麦 ... ");
        mProgressDialog.show();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                startConferenceInternal();
            }
        });
        return true;
    }

    private boolean startConferenceInternal() {
        String roomToken = mToken;//StreamUtils.requestRoomToken(mRoomName);
        if (roomToken == null) {
            dismissProgressDialog();
            showToast("无法获取房间信息 !", Toast.LENGTH_SHORT);
            return false;
        }

        String UserID = mRoomId;//StreamUtils.DEFAULT_RTC_USER_ID;
        //showToast("传入的连麦参数"+"Id==>"+UserID+";"+"roomname==>"+mRoomName+";"+"roomToken==>"+roomToken+";", Toast.LENGTH_SHORT);
        Log.d(TAG, "startConferenceInternal: UserId ==> "+UserID+";"+"RoomName==> "+mRoomName+";"+"roomToken==> "+roomToken);
        mRTCStreamingManager.startConference(UserID, mRoomName, roomToken, new RTCStartConferenceCallback() {
            @Override
            public void onStartConferenceSuccess() {
                dismissProgressDialog();
                showToast("开始连麦", Toast.LENGTH_SHORT);
                updateControlButtonText();
                mIsConferenceStarted = true;
                /**
                 * Because `startConference` is called in child thread
                 * So we should check if the activity paused.
                 */
                if (mIsActivityPaused) {
                    stopConference();
                }
            }

            @Override
            public void onStartConferenceFailed(int errorCode) {
                setConferenceBoxChecked(false);
                dismissProgressDialog();
                showToast("无法成功开启连麦:无法成功开启连麦，错误码：" + errorCode, Toast.LENGTH_SHORT);
            }
        });
        return true;
    }

    private boolean stopConference() {
        //恢复复布局
        ReChangeLayout();

        if (!mIsConferenceStarted) {
            return true;
        }

        mRTCStreamingManager.stopConference();
        mIsConferenceStarted = false;
        setMuteCheckBoxChecked(false);
        setConferenceBoxChecked(false);
        showToast(getString(R.string.stop_conference), Toast.LENGTH_SHORT);
        updateControlButtonText();
        return true;
    }

    //设置连麦或者推流出去的画面是否镜像
    public void setPublishMirror(boolean mirror){
        mRTCStreamingManager.setEncodingMirror( mirror);
    }

    private boolean startPublishStreaming() {
        if (mIsPublishStreamStarted) {
            return true;
        }
        if (!mIsInReadyState) {
            showToast(getString(R.string.stream_state_not_ready), Toast.LENGTH_SHORT);
            return false;
        }
        mProgressDialog.setMessage("正在准备推流... ");
        mProgressDialog.show();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                startPublishStreamingInternal();
            }
        });
        return true;
    }

    private boolean startPublishStreamingInternal() {
        String publishAddr = mLiveUrl;//StreamUtils.requestPublishAddress(mRoomName);
        if (publishAddr == null) {
            dismissProgressDialog();
            showToast("无法获取推流地址 !", Toast.LENGTH_SHORT);
            return false;
        }

        try {
            if (StreamUtils.IS_USING_STREAMING_JSON) {
                mStreamingProfile.setStream(new StreamingProfile.Stream(new JSONObject(publishAddr)));
            } else {
                mStreamingProfile.setPublishUrl(publishAddr);

                showToast("推流地址:"+publishAddr, Toast.LENGTH_SHORT);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            dismissProgressDialog();
            showToast("无效的推流地址 !", Toast.LENGTH_SHORT);
            return false;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            dismissProgressDialog();
            showToast("无效的推流地址 !", Toast.LENGTH_SHORT);
            return false;
        }

        mRTCStreamingManager.setStreamingProfile(mStreamingProfile);
        if (!mRTCStreamingManager.startStreaming()) {
            dismissProgressDialog();
            showToast(getString(R.string.failed_to_start_streaming), Toast.LENGTH_SHORT);
            return false;
        }
        dismissProgressDialog();
        showToast(getString(R.string.start_streaming), Toast.LENGTH_SHORT);
        updateControlButtonText();
        mIsPublishStreamStarted = true;
        /**
         * Because `startPublishStreaming` need a long time in some weak network
         * So we should check if the activity paused.
         */
        if (mIsActivityPaused) {
            stopPublishStreaming();
        }
        return true;
    }

    private boolean stopPublishStreaming() {
        if (!mIsPublishStreamStarted) {
            return true;
        }
        mIsInReadyState = false;
        mRTCStreamingManager.stopStreaming();
        mIsPublishStreamStarted = false;
        setMuteCheckBoxChecked(false);
        showToast(getString(R.string.stop_streaming), Toast.LENGTH_SHORT);
        setStatusText(getString(R.string.streaming_stopping));
        updateControlButtonText();
        return false;
    }

    private StreamingStateChangedListener mStreamingStateChangedListener = new StreamingStateChangedListener() {
        @Override
        public void onStateChanged(final StreamingState state, Object o) {
            switch (state) {
                case PREPARING:
                    setStatusText(getString(R.string.preparing));
                    Log.d(TAG, "onStateChanged state:" + "preparing");
                    break;
                case READY:
                    mIsInReadyState = true;
                    setStatusText(getString(R.string.ready));
                    Log.d(TAG, "onStateChanged state:" + "ready");
                    break;
                case CONNECTING:
                    Log.d(TAG, "onStateChanged state:" + "connecting");
                    break;
                case STREAMING:
                    setStatusText(getString(R.string.streaming));
                    Log.d(TAG, "onStateChanged state:" + "streaming");
                    break;
                case SHUTDOWN:
                    mIsInReadyState = true;
                    setStatusText(getString(R.string.ready));
                    Log.d(TAG, "onStateChanged state:" + "shutdown");
                    break;
                case IOERROR:
                    Log.d(TAG, "onStateChanged state:" + "io error");
                    showToast(getString(R.string.io_error), Toast.LENGTH_SHORT);
                    stopPublishStreaming();
                    break;
                case UNKNOWN:
                    Log.d(TAG, "onStateChanged state:" + "unknown");
                    break;
                case SENDING_BUFFER_EMPTY:
                    Log.d(TAG, "onStateChanged state:" + "sending buffer empty");
                    break;
                case SENDING_BUFFER_FULL:
                    Log.d(TAG, "onStateChanged state:" + "sending buffer full");
                    break;
                case AUDIO_RECORDING_FAIL:
                    Log.d(TAG, "onStateChanged state:" + "audio recording failed");
                    showToast(getString(R.string.failed_open_microphone), Toast.LENGTH_SHORT);
                    stopPublishStreaming();
                    break;
                case OPEN_CAMERA_FAIL:
                    Log.d(TAG, "onStateChanged state:" + "open camera failed");
                    showToast(getString(R.string.failed_open_camera), Toast.LENGTH_SHORT);
                    stopPublishStreaming();
                    break;
                case DISCONNECTED:
                    Log.d(TAG, "onStateChanged state:" + "disconnected");
                    setStatusText(getString(R.string.disconnected));
                    stopPublishStreaming();
                    break;
                case TORCH_INFO:
                    Log.d(TAG, "onStateChanged state:" + "TORCH_INFO");
                    setStatusText("无效URL");
                    break;
                case INVALID_STREAMING_URL:
                    Log.d(TAG, "onStateChanged state:" + "INVALID_STREAMING_URL");
                    setStatusText("无效URL");
                    break;
            }
        }
    };

    private StreamingSessionListener mStreamingSessionListener = new StreamingSessionListener() {
        @Override
        public boolean onRecordAudioFailedHandled(int code) {
            return false;
        }

        @Override
        public boolean onRestartStreamingHandled(int code) {
            // You can do reconnect here, make sure check network first.
            if (!StreamUtils.isNetworkAvailable()) {
                showToast(getString(R.string.network_disconnected), Toast.LENGTH_SHORT);
                finish();
                return false;
            }
            mIsInReadyState = true;
            return startPublishStreamingInternal();
        }

        @Override
        public Camera.Size onPreviewSizeSelected(List<Camera.Size> list) {
            return null;
        }
    };

    private RTCUserEventListener mRTCUserEventListener = new RTCUserEventListener(){
        /**
         * 远端用户成功加入连麦
         * @param userId 远端用户的 userId
         */
        @Override
        public void onUserJoinConference(String userId){

        }
        /**
         * 远端用户退出连麦
         * @param userId 远端用户的 userId
         */
        @Override
        public void onUserLeaveConference(String userId){
            showToast("远端用户退出连麦:"+userId, Toast.LENGTH_SHORT);
            stopConference();
        }
    };

    private RTCConferenceStateChangedListener mRTCStreamingStateChangedListener = new RTCConferenceStateChangedListener() {
        @Override
        public void onConferenceStateChanged(RTCConferenceState state, int extra) {
            Log.e(TAG, "onConferenceStateChanged: rtc state ==> "+ state );
            switch (state) {
                case READY:
                    // You must `StartConference` after `Ready`
                    showToast(getString(R.string.ready), Toast.LENGTH_SHORT);
                    break;
                /**
                 * 正在连接服务器
                 * 当网络断开后，会回调该状态，自动重连服务器
                 */
                case CONNECTING:
                    showToast("正在连接服务器", Toast.LENGTH_SHORT);
                    break;
                /**
                 * 已经连接上服务器
                 */
                case CONNECTED:
                    showToast("已经连接上服务器", Toast.LENGTH_SHORT);
                    break;
                /**
                 * 多次尝试连接服务器均失败后，会回调该状态
                 */
                case CONNECT_FAIL:
                    showToast("多次尝试连接服务器均失败", Toast.LENGTH_SHORT);
                    stopConference();
                    //finish();
                    break;
                case VIDEO_PUBLISH_FAILED:
                    showToast("无法发布视频到连麦房间" + extra, Toast.LENGTH_SHORT);
                    stopConference();
                    //finish();
                    break;
                case AUDIO_PUBLISH_FAILED:
                    showToast("无法发布音频到连麦房间" + extra, Toast.LENGTH_SHORT);
                    stopConference();
                    //finish();
                    break;
                case VIDEO_PUBLISH_SUCCESS:
                    showToast("成功发布视频到连麦房间", Toast.LENGTH_SHORT);
                    break;
                case AUDIO_PUBLISH_SUCCESS:
                    showToast("成功发布音频到连麦房间", Toast.LENGTH_SHORT);
                    break;
                case USER_JOINED_AGAIN:
                    showToast("用户在其它地方登录", Toast.LENGTH_SHORT);
                    stopConference();
//                    finish();
                    break;
                case USER_KICKOUT_BY_HOST:
                    showToast("用户被管理员踢出房间", Toast.LENGTH_SHORT);
                    stopConference();
//                    finish();
                    break;
                case OPEN_CAMERA_FAIL:
                    showToast("打开摄像头失败", Toast.LENGTH_SHORT);
                    break;
                case AUDIO_RECORDING_FAIL:
                    showToast("打开音频失败", Toast.LENGTH_SHORT);
                    break;
                default:
                    return;
            }
        }
    };

    private RTCRemoteWindowEventListener mRTCRemoteWindowEventListener = new RTCRemoteWindowEventListener() {
        @Override
        public void onRemoteWindowAttached(RTCVideoWindow window, String remoteUserId) {
            //showToast("远端连麦视频窗口[显示]事件:"+remoteUserId, Toast.LENGTH_SHORT);
            Log.d(TAG, "onRemoteWindowAttached: " + remoteUserId);
        }

        @Override
        public void onRemoteWindowDetached(RTCVideoWindow window, String remoteUserId) {
            //showToast("远端连麦视频窗口[隐藏]事件:"+remoteUserId, Toast.LENGTH_SHORT);
            stopConference();
            Log.d(TAG, "onRemoteWindowDetached: " + remoteUserId);
        }

        @Override
        public void onFirstRemoteFrameArrived(String remoteUserId) {
            Log.d(TAG, "onFirstRemoteFrameArrived: " + remoteUserId);
        }
    };

    private View.OnClickListener mMuteButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mIsConferenceStarted && !mIsPublishStreamStarted) {
                mMuteCheckBox.setChecked(!mMuteCheckBox.isChecked());
                showToast(getString(R.string.mute_must_in_streaming_status), Toast.LENGTH_SHORT);
                return;
            }
            mRTCStreamingManager.mute(mMuteCheckBox.isChecked());
        }
    };

    private void ChangeLayout(){
        //连麦自己的布局
        ViewGroup.LayoutParams  self_lp = mAfl.getLayoutParams();
        self_lp.height = mScreenHeight / 2;
        self_lp.width  = mScreenWidth / 2;

        mAfl.setLayoutParams(self_lp);
        mCameraView.setLayoutParams(self_lp);

        //连麦对方的布局
        ViewGroup.LayoutParams peer_lp = mPeerfl.getLayoutParams();
        peer_lp.height = mScreenHeight / 2;
        peer_lp.width  = mScreenWidth / 2;

        mPeerfl.setLayoutParams(peer_lp);
        mPeerfl.setVisibility(View.VISIBLE);
        mPeerView.setLayoutParams(peer_lp);
        mPeerView.setVisibility(View.VISIBLE);
    }
    void ReChangeLayout(){
        //结束连麦自己的布局
        ViewGroup.LayoutParams  self_lp = mAfl.getLayoutParams();
        self_lp.height = mScreenHeight;
        self_lp.width  = mScreenWidth;

        mAfl.setLayoutParams(self_lp);
        mCameraView.setLayoutParams(self_lp);

        //结束连麦对方的布局
        mPeerfl.setVisibility(View.INVISIBLE);
        mPeerView.setVisibility(View.INVISIBLE);

    }
    private View.OnClickListener mConferenceButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mConferenceCheckBox.isChecked()) {
                //开始连麦
                startConference();
            } else {
                //结束连麦
                stopConference();
            }
        }
    };

    public void onClickStreaming(View v) {
        if (!mIsPublishStreamStarted) {
            startPublishStreaming();
        } else {
            stopPublishStreaming();
        }
    }

    private void setStatusText(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setText(status);
            }
        });
    }

    private void updateControlButtonText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mIsPublishStreamStarted) {
                    mControlButton.setText(getString(R.string.stop_streaming));
                } else {
                    mControlButton.setText(getString(R.string.start_streaming));
                }
            }
        });
    }

    private void setMuteCheckBoxChecked(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMuteCheckBox.setChecked(enabled);
            }
        });
    }

    private void setConferenceBoxChecked(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConferenceCheckBox.setChecked(enabled);
            }
        });
    }

    private StreamStatusCallback mStreamStatusCallback = new StreamStatusCallback() {
        @Override
        public void notifyStreamStatusChanged(final StreamingProfile.StreamStatus streamStatus) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String stat = "bitrate: " + streamStatus.totalAVBitrate / 1024 + " kbps"
                            + "\naudio: " + streamStatus.audioFps + " fps"
                            + "\nvideo: " + streamStatus.videoFps + " fps";
                    mStatTextView.setText(stat);
                }
            });
        }
    };

    private void dismissProgressDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog.dismiss();
            }
        });
    }

    private void showToast(final String text, final int duration) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, text, duration).show();
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveActivity.this, text, duration).show();
            }
        });
    }

    private CameraStreamingSetting.CAMERA_FACING_ID chooseCameraFacingId() {
//        if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD)) {
//            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
//        } else if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT)) {
//            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
//        } else {
//            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
//        }
        return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
    }

    @Override
    public void onAudioSourceAvailable(ByteBuffer byteBuffer, int i, long l, boolean b) {
        if (rtcRecord)
            writeAudioDataToFile(byteBuffer, i);
    }

    /**
     * 采集音频数据
     * rtcRecord 控制采集开始和结束的逻辑
     * 结束的时候调用 stopRecording（）
     */

    boolean rtcRecord = false;

    private int bufferSize = 0;
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private String getFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV);
    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        File tempFile = new File(filepath, AUDIO_RECORDER_TEMP_FILE);

        if (tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    FileOutputStream os = null;

    private void writeAudioDataToFile(ByteBuffer byteBuffer, int bufferSize) {
        String filename = getTempFilename();
        this.bufferSize = bufferSize;

        try {
            os = new FileOutputStream(filename, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            os.write(byteBuffer.array(), 0, bufferSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        copyWaveFile(getTempFilename(), getFilename());
        deleteTempFile();
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 1;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}
