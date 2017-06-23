package com.qiniu.pili.droid.rtcstreaming.demo.activity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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

import com.pili.pldroid.player.AVOptions;
import com.pili.pldroid.player.PLMediaPlayer;
import com.pili.pldroid.player.widget.PLVideoView;
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
import com.qiniu.pili.droid.streaming.widget.AspectFrameLayout;
import com.qiniu.pili.droid.rtcstreaming.demo.utils.Utils;

public class AudienceActivity extends AppCompatActivity {

    private static final String TAG = AudienceActivity.class.getSimpleName();

    private TextView mStatusTextView;

    private Button mControlButton;
    private CheckBox mMuteCheckBox;
    private CheckBox mMirrorCheckBox;

    private ProgressDialog mProgressDialog;

    private RTCMediaStreamingManager mRTCStreamingManager;

    private StreamingProfile mStreamingProfile;

    private boolean mIsActivityPaused = true;
    private boolean mIsPublishStreamStarted = false;
    private boolean mIsConferenceStarted = false;
    private boolean mIsInReadyState = false;
    private int     mCurrentCamFacingIndex;

    private String mLiveUrl;
    private String mToken;
    private String mRoomId;

    private FrameLayout mPeerfl;
    private GLSurfaceView mPeerView;
    private GLSurfaceView mCameraView;
    private AspectFrameLayout mCamerafl;

    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    //////////////////////////////////////////////////
    private View mLoadingView;
    private PLVideoView mVideoView;
    private Toast mToast = null;
    private String mVideoPath = null;
    private String mRoomName;
    private String mRoomToken;

    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    startConference();
                    break;
                case 200:
                    /*if (mIsActivityPaused || !Utils.isLiveStreamingAvailable()) {
                        finish();
                        return;
                    }
                    if (!Utils.isNetworkAvailable(AudienceActivity.this)) {
                        sendReconnectMessage();
                        return;
                    }
                    mVideoView.setVideoPath(mVideoPath);
                    mVideoView.start();*/

                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(com.qiniu.pili.droid.rtcstreaming.demo.R.layout.activity_playback);
        mVideoView = (PLVideoView) findViewById(com.qiniu.pili.droid.rtcstreaming.demo.R.id.VideoView);

        mLoadingView = findViewById(com.qiniu.pili.droid.rtcstreaming.demo.R.id.LoadingView);
        mVideoView.setBufferingIndicator(mLoadingView);

        mVideoPath = getIntent().getStringExtra("videoPath");
        mRoomId  = getIntent().getStringExtra("roomId");
        mRoomName  = getIntent().getStringExtra("roomName");
        mRoomToken = getIntent().getStringExtra("token");

        mLiveUrl = mVideoPath;
        mToken   = mRoomToken;

        AVOptions options = new AVOptions();
        options.setInteger(AVOptions.KEY_PREPARE_TIMEOUT, 10 * 1000);
        options.setInteger(AVOptions.KEY_GET_AV_FRAME_TIMEOUT, 10 * 1000);
        options.setInteger(AVOptions.KEY_LIVE_STREAMING, 1);
        options.setInteger(AVOptions.KEY_DELAY_OPTIMIZATION, 1);

        // 1 -> hw codec enable, 0 -> disable [recommended]
        int codec = getIntent().getIntExtra("mediaCodec", 0);
        options.setInteger(AVOptions.KEY_MEDIACODEC, codec);

        // whether start play automatically after prepared, default value is 1
        options.setInteger(AVOptions.KEY_START_ON_PREPARED, 0);

        mVideoView.setAVOptions(options);
        mVideoView.setDisplayAspectRatio(PLVideoView.ASPECT_RATIO_PAVED_PARENT);

        // Set some listeners
        mVideoView.setOnInfoListener(mOnInfoListener);
        mVideoView.setOnCompletionListener(mOnCompletionListener);
        mVideoView.setOnErrorListener(mOnErrorListener);

        mVideoView.setVideoPath(mVideoPath);

        //获取屏幕宽高
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        mScreenWidth = wm.getDefaultDisplay().getWidth();
        mScreenHeight = wm.getDefaultDisplay().getHeight();

        RTCMediaStreamingManager.init(getApplicationContext());
        ConferenceInit();
    }

    public void ConferenceInit(){
        //自己的显示布局
        mCamerafl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        ViewGroup.LayoutParams  self_lp = mCamerafl.getLayoutParams();
        self_lp.height = mScreenHeight / 2;
        self_lp.width  = mScreenWidth / 2;
        mCamerafl.setShowMode(AspectFrameLayout.SHOW_MODE.FULL);
        mCamerafl.setLayoutParams(self_lp);
        mCamerafl.setVisibility(View.VISIBLE);

        mCameraView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);
        mCameraView.setLayoutParams(self_lp);
        mCameraView.setVisibility(View.INVISIBLE);

        mControlButton = (Button) findViewById(R.id.ConferenceBtn);
        mStatusTextView = (TextView) findViewById(R.id.StatusTextView);
        setStatusText("我是观众");

        mMirrorCheckBox = (CheckBox)findViewById(R.id.MirrorCheckBox);
        mMirrorCheckBox.setOnClickListener(mMirrorButtonClickListener);
        mMirrorCheckBox.setVisibility(View.INVISIBLE);

        mMuteCheckBox = (CheckBox) findViewById(R.id.MuteCheckBox);
        mMuteCheckBox.setOnClickListener(mMuteButtonClickListener);
        mMuteCheckBox.setVisibility(View.INVISIBLE);

        // Config the inner camera settings
        CameraStreamingSetting.CAMERA_FACING_ID facingId = chooseCameraFacingId();
        mCurrentCamFacingIndex = facingId.ordinal();
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

        mRTCStreamingManager = new RTCMediaStreamingManager(getApplicationContext(), mCamerafl, mCameraView);
        mRTCStreamingManager.setConferenceStateListener(mRTCStreamingStateChangedListener);
        mRTCStreamingManager.setRemoteWindowEventListener(mRTCRemoteWindowEventListener);
        mRTCStreamingManager.setUserEventListener(mRTCUserEventListener);
        mRTCStreamingManager.setDebugLoggingEnabled(false);

        RTCConferenceOptions options = new RTCConferenceOptions();

        // vice anchor can use a smaller size
        // RATIO_4_3 & VIDEO_ENCODING_SIZE_HEIGHT_240 means the output size is 320 x 240
        // 4:3 looks better in the mix frame
        options.setVideoEncodingSizeRatio(RTCConferenceOptions.VIDEO_ENCODING_SIZE_RATIO.RATIO_4_3);
        options.setVideoEncodingSizeLevel(RTCConferenceOptions.VIDEO_ENCODING_SIZE_HEIGHT_480);
        // vice anchor can use a higher conference bitrate for better image quality
        options.setVideoBitrateRange(800 * 1000, 1000 * 1000);
        // 24 fps is enough
        options.setVideoEncodingFps(25);

        mRTCStreamingManager.setConferenceOptions(options);

        // 远端显示布局
        mPeerfl = (FrameLayout) findViewById(R.id.RemoteWindowA);
        mPeerView = (GLSurfaceView)findViewById(R.id.RemoteGLSurfaceViewA);
        ViewGroup.LayoutParams peer_lp = mPeerfl.getLayoutParams();
        peer_lp.height = mScreenHeight / 2;
        peer_lp.width  = mScreenWidth / 2;

        mPeerfl.setLayoutParams(peer_lp);
        mPeerfl.setVisibility(View.INVISIBLE);
        mPeerView.setLayoutParams(peer_lp);
        mPeerView.setVisibility(View.INVISIBLE);

        //添加到显示窗口到RTC
        RTCVideoWindow windowA = new RTCVideoWindow(mPeerfl, mPeerView);
        mRTCStreamingManager.addRemoteWindow(windowA);

        // the anchor must configure the `StreamingProfile`
        mControlButton.setText("开始连麦");

        mRTCStreamingManager.prepare(cameraStreamingSetting, null);

        mProgressDialog = new ProgressDialog(this);
    }

    public void StartCapture(){
        mIsActivityPaused = false;
        mRTCStreamingManager.startCapture();
    }

    public void StopCapture(){
        if(mIsActivityPaused==false){
            mIsActivityPaused = true;
//        stopConference();
            mRTCStreamingManager.stopCapture();
        }
    }

    //设置连麦或者推流出去的画面是否镜像
    public void setPublishMirror(boolean mirror){
        mRTCStreamingManager.setEncodingMirror( mirror);
    }

    public void SwitchCamera(){
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

    private boolean startConference() {
        if (mIsConferenceStarted) {
            return true;
        }
        setStatusText("我是副主播");
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

        String UserID = mRoomId;//StreamUtils.PEER_RTC_USER_ID;

        mRTCStreamingManager.startConference(UserID, mRoomName, roomToken, new RTCStartConferenceCallback() {
            @Override
            public void onStartConferenceSuccess() {
                dismissProgressDialog();
                showToast(getString(R.string.start_conference), Toast.LENGTH_SHORT);
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
                dismissProgressDialog();
                showToast(getString(R.string.failed_to_start_conference) + errorCode, Toast.LENGTH_SHORT);
            }
        });
        return true;
    }

    private boolean stopConference() {
        if (!mIsConferenceStarted) {
            return true;
        }

        setStatusText("我是观众");
        mRTCStreamingManager.stopConference();
        mIsConferenceStarted = false;
        setMuteCheckBoxChecked(false);
        setMirrorCheckBoxChecked(false);
        showToast(getString(R.string.stop_conference), Toast.LENGTH_SHORT);
        updateControlButtonText();
        return true;
    }

    private RTCConferenceStateChangedListener mRTCStreamingStateChangedListener = new RTCConferenceStateChangedListener() {
        @Override
        public void onConferenceStateChanged(RTCConferenceState state, int extra) {
            switch (state) {
                case READY:
                    // You must `StartConference` after `Ready`
                    Message message = new Message();
                    message.what = 100;
                    mHandler.sendMessage(message);
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
                    //finish();
                    break;
                case VIDEO_PUBLISH_FAILED:
                    showToast("无法发布视频到连麦房间" + extra, Toast.LENGTH_SHORT);
                    //finish();
                    break;
                case AUDIO_PUBLISH_FAILED:
                    showToast("无法发布音频到连麦房间" + extra, Toast.LENGTH_SHORT);
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
//                    finish();
                    break;
                case USER_KICKOUT_BY_HOST:
                    showToast("用户被管理员踢出房间", Toast.LENGTH_SHORT);
//                    finish();
                    break;
                case OPEN_CAMERA_FAIL:
                    showToast("打开摄像头失败", Toast.LENGTH_SHORT);
                    break;
                case AUDIO_RECORDING_FAIL:
                    showToast("打开麦克风失败", Toast.LENGTH_SHORT);
                    break;
                default:
                    return;
            }
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
            showToast("用户离开房间："+userId, Toast.LENGTH_SHORT);
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
            //停止摄像头和麦克风采集
            StopCapture();
            //停止连麦
            stopConference();
            //隐藏连麦布局
            mMuteCheckBox.setVisibility(View.INVISIBLE);
            mMirrorCheckBox.setVisibility(View.INVISIBLE);
            mCamerafl.setVisibility(View.INVISIBLE);
            mCameraView.setVisibility(View.INVISIBLE);
            mPeerfl.setVisibility(View.INVISIBLE);
            mPeerView.setVisibility(View.INVISIBLE);
            //播放直播流
            mVideoView.setVideoPath(mVideoPath);
            mVideoView.start();
            //显示播放布局
            mVideoView.getSurfaceView().setVisibility(View.VISIBLE);

            Log.d(TAG, "onRemoteWindowDetached: " + remoteUserId);
        }

        @Override
        public void onFirstRemoteFrameArrived(String remoteUserId) {
            Log.d(TAG, "onFirstRemoteFrameArrived: " + remoteUserId);
        }
    };


    private View.OnClickListener mMirrorButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mIsConferenceStarted && !mIsPublishStreamStarted) {
                mMirrorCheckBox.setChecked(!mMirrorCheckBox.isChecked());
                showToast("必须开始连麦或者推流后才能镜像", Toast.LENGTH_SHORT);
                return;
            }
            mRTCStreamingManager.setEncodingMirror(mMirrorCheckBox.isChecked());
        }
    };

    private View.OnClickListener mMuteButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mIsConferenceStarted && !mIsPublishStreamStarted) {
                mMuteCheckBox.setChecked(!mMuteCheckBox.isChecked());
                showToast("必须开始连麦或者推流后才能静音", Toast.LENGTH_SHORT);
                return;
            }
            mRTCStreamingManager.mute(mMuteCheckBox.isChecked());
        }
    };

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
                if (mIsConferenceStarted) {
                    mControlButton.setText(getString(R.string.stop_conference));
                } else {
                    mControlButton.setText(getString(R.string.start_conference));
                }
            }
        });
    }

    private void setMirrorCheckBoxChecked(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMirrorCheckBox.setChecked(enabled);
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
                Toast.makeText(AudienceActivity.this, text, duration).show();
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
    protected void onResume() {
        super.onResume();
        mVideoView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVideoView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mVideoView.stopPlayback();

        StopCapture();

        stopConference();

        if(mRTCStreamingManager!=null)
            mRTCStreamingManager.destroy();
        RTCMediaStreamingManager.deinit();
    }

    public void onClickExit(View v) {
        finish();
    }

    // 当用户点击连麦以后，建议向 App Server 申请向主播连麦，主播同意后，方可进入房间，完成连麦
    public void onClickConference(View v) {
        if (!mIsConferenceStarted) {
            //隐藏播放布局
            mVideoView.stopPlayback();
            mVideoView.getSurfaceView().setVisibility(View.INVISIBLE);

            //打开摄像头和麦克风
            StartCapture();
            //开启摄像头后要等回调READY，才能开始连麦 startConference();

            //显示连麦布局
            mMuteCheckBox.setVisibility(View.VISIBLE);
            mMirrorCheckBox.setVisibility(View.VISIBLE);
            mCamerafl.setVisibility(View.VISIBLE);
            mCameraView.setVisibility(View.VISIBLE);
            mPeerfl.setVisibility(View.VISIBLE);
            mPeerView.setVisibility(View.VISIBLE);

        } else {
            //停止摄像头和麦克风采集
            StopCapture();
            //停止连麦
            stopConference();

            //隐藏连麦布局
            mMuteCheckBox.setVisibility(View.INVISIBLE);
            mMirrorCheckBox.setVisibility(View.INVISIBLE);
            mCamerafl.setVisibility(View.INVISIBLE);
            mCameraView.setVisibility(View.INVISIBLE);
            mPeerfl.setVisibility(View.INVISIBLE);
            mPeerView.setVisibility(View.INVISIBLE);

            //开始播放直播流
            mVideoView.setVideoPath(mVideoPath);
            mVideoView.start();
            //显示播放布局
            mVideoView.getSurfaceView().setVisibility(View.VISIBLE);
        }
    }

    private PLMediaPlayer.OnInfoListener mOnInfoListener = new PLMediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(PLMediaPlayer plMediaPlayer, int what, int extra) {
            Log.d(TAG, "onInfo: " + what + ", " + extra);
            return false;
        }
    };

    private PLMediaPlayer.OnErrorListener mOnErrorListener = new PLMediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(PLMediaPlayer plMediaPlayer, int errorCode) {
            Log.e(TAG, "Error happened, errorCode = " + errorCode);
            switch (errorCode) {
                case PLMediaPlayer.ERROR_CODE_INVALID_URI:
                    showToastTips("Invalid URL !");
                    break;
                case PLMediaPlayer.ERROR_CODE_404_NOT_FOUND:
                    showToastTips("404 resource not found !");
                    break;
                case PLMediaPlayer.ERROR_CODE_CONNECTION_REFUSED:
                    showToastTips("Connection refused !");
                    break;
                case PLMediaPlayer.ERROR_CODE_CONNECTION_TIMEOUT:
                    showToastTips("Connection timeout !");
                    break;
                case PLMediaPlayer.ERROR_CODE_EMPTY_PLAYLIST:
                    showToastTips("Empty playlist !");
                    break;
                case PLMediaPlayer.ERROR_CODE_STREAM_DISCONNECTED:
                    showToastTips("Stream disconnected !");
                    break;
                case PLMediaPlayer.ERROR_CODE_IO_ERROR:
                    showToastTips("Network IO Error !");
                    break;
                case PLMediaPlayer.ERROR_CODE_UNAUTHORIZED:
                    showToastTips("Unauthorized Error !");
                    break;
                case PLMediaPlayer.ERROR_CODE_PREPARE_TIMEOUT:
                    showToastTips("Prepare timeout !");
                    break;
                case PLMediaPlayer.ERROR_CODE_READ_FRAME_TIMEOUT:
                    showToastTips("Read frame timeout !");
                    break;
                case PLMediaPlayer.MEDIA_ERROR_UNKNOWN:
                default:
                    showToastTips("unknown error !");
                    break;
            }
            // Todo pls handle the error status here, retry or call finish()
            finish();
            // If you want to retry, do like this:
            // mVideoView.setVideoPath(mVideoPath);
            // mVideoView.start();
            // Return true means the error has been handled
            // If return false, then `onCompletion` will be called
            return true;
        }
    };

    private PLMediaPlayer.OnCompletionListener mOnCompletionListener = new PLMediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(PLMediaPlayer plMediaPlayer) {
            Log.d(TAG, "Play Completed !");
            showToastTips("Play Completed !");
            finish();
        }
    };

    private static final int MESSAGE_ID_RECONNECTING = 0x01;

    /**
    To do
    播放端重连 mHandler 中处理；
     */

    private void sendReconnectMessage() {
        showToastTips("正在重连...");
        mLoadingView.setVisibility(View.VISIBLE);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_ID_RECONNECTING), 500);
    }

    private void showToastTips(final String tips) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast != null) {
                    mToast.cancel();
                }
                mToast = Toast.makeText(AudienceActivity.this, tips, Toast.LENGTH_SHORT);
                mToast.show();
            }
        });
    }
}
