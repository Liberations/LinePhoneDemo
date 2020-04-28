package com.iisfree.linephone;

import android.util.Log;

import org.linphone.core.AccountCreator;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.CallListener;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.Core;
import org.linphone.core.Headers;
import org.linphone.core.InfoMessage;
import org.linphone.core.MediaDirection;
import org.linphone.core.ProxyConfig;
import org.linphone.core.TransportType;

public class CallManager {

    private static final String TAG = "CallManager";
    private volatile static CallManager singleton;
    CallListener callListener = new CallListener() {
        @Override
        public void onSnapshotTaken(Call call, String s) {

        }

        @Override
        public void onStateChanged(Call call, Call.State state, String s) {

        }

        @Override
        public void onTransferStateChanged(Call call, Call.State state) {

        }

        @Override
        public void onTmmbrReceived(Call call, int i, int i1) {

        }

        @Override
        public void onInfoMessageReceived(Call call, InfoMessage infoMessage) {

        }

        @Override
        public void onEncryptionChanged(Call call, boolean b, String s) {

        }

        @Override
        public void onAckProcessing(Call call, Headers headers, boolean b) {
            Log.d(TAG, "onAckProcessing: " + headers.toString());

        }

        @Override
        public void onDtmfReceived(Call call, int i) {

        }

        @Override
        public void onNextVideoFrameDecoded(Call call) {
            Log.d(TAG, "onNextVideoFrameDecoded: 视频解码");

        }

        @Override
        public void onStatsUpdated(Call call, CallStats callStats) {

        }
    };

    private CallManager() {

    }

    public static CallManager getInstance() {
        if (singleton == null) {
            synchronized (CallManager.class) {
                if (singleton == null) {
                    singleton = new CallManager();

                }
            }
        }
        return singleton;
    }


    /**
     * 注冊用戶
     *
     * @param serverAddress SIP服务器地址 192.168.0.195
     * @param userName      用户名 1022
     * @param pwd           密码 1234
     */
    public void registerUser(String serverAddress, String userName, String pwd) {
        //注册前先移除之前注册的用户
        if (!LinePhoneService.isReady()) {
            Log.d(TAG, "通话服务启动中,请稍后再试: ");
            return;
        }
        Core core = LinePhoneService.getCore();
        if (core == null) return;
        ProxyConfig[] proxyConfigs = core.getProxyConfigList();
        if (proxyConfigs != null && proxyConfigs.length > 0) {
            for (int i = 0; i < proxyConfigs.length; i++) {
                ProxyConfig proxyConfig = proxyConfigs[i];
                AuthInfo mAuthInfo = proxyConfig.findAuthInfo();
                if (proxyConfig != null) {
                    core.removeProxyConfig(proxyConfig);
                }
                if (mAuthInfo != null) {
                    core.removeAuthInfo(mAuthInfo);
                }

            }
        }
        core.clearAllAuthInfo();
        //注册sip
        AccountCreator mAccountCreator = LinePhoneService.getCore().createAccountCreator(null);
        mAccountCreator.setUsername(userName);
        mAccountCreator.setDomain(serverAddress);
        mAccountCreator.setPassword(pwd);
        mAccountCreator.setTransport(TransportType.Udp);
        AccountCreator.Status status = mAccountCreator.setAsDefault(true);
        Log.d(TAG, serverAddress+"----"+userName + "----: " + pwd + status);

        //设置当前账号
        // This will automatically create the proxy config and auth info and add them to the Core
        ProxyConfig cfg = mAccountCreator.createProxyConfig();
        // Make sure the newly created one is the default
        LinePhoneService.getCore().setDefaultProxyConfig(cfg);
    }

    /*查找当前未接通电话*/
    public Call getCurrentCall() {
        if (LinePhoneService.getCore() != null) {
            for (Call call : LinePhoneService.getCore().getCalls()) {
                if (Call.State.IncomingReceived == call.getState()
                        || Call.State.IncomingEarlyMedia == call.getState()) {
                    return call;
                }
            }
        }
        return null;
    }

    /*查找当前通话中电话*/
    public Call getNowCall() {
        if (LinePhoneService.getCore() != null) {
            for (Call call : LinePhoneService.getCore().getCalls()) {
                if (Call.State.Connected == call.getState()
                        || Call.State.StreamsRunning == call.getState()) {
                    return call;
                }
            }
        }
        return null;
    }


    /**
     * 呼叫
     *
     * @param num 对方用户名 如1021
     */
    public void callPhone(String num) {
        Core core = LinePhoneService.getCore();
        if (core == null) return;
        Address addressToCall = core.interpretUrl(num);
        CallParams params = core.createCallParams(null);
        params.setVideoDirection(MediaDirection.Invalid);
        params.enableVideoMulticast(false);
        params.enableVideo(false);
        if (addressToCall != null) {
            core.inviteAddressWithParams(addressToCall, params);
        }
    }

    /**
     * 接听语音电话
     */
    public boolean acceptCall() {
        Call call = getCurrentCall();
        if (call == null) return false;
        Core core = LinePhoneService.getCore();
        CallParams params = core.createCallParams(call);
        if (params != null) {
            call.acceptWithParams(params);
            return true;
        }
        return false;
    }

    /**
     * 接听视频电话
     */
    public boolean acceptVideoCall() {
        Call call = getCurrentCall();
        if (call == null) return false;
        Core core = LinePhoneService.getCore();
        CallParams params = core.createCallParams(call);
        params.enableVideo(true);
        params.setVideoDirection(MediaDirection.RecvOnly);
        //通话文件录制
        // params.setRecordFile(App.getInstance().getPath() + "/" + call.getRemoteAddressAsString() + "_" + System.currentTimeMillis() + ".mkv");
        // Log.d(TAG, "acceptVideoCall: " + params.getRecordFile());
        if (params != null) {
            call.acceptWithParams(params);
            //开始录制
            //call.startRecording();
            call.addListener(callListener);
            return true;
        }
        return false;
    }


    /**
     * 挂断电话
     */
    public void handUp() {
        try {
            Core core = LinePhoneService.getCore();
            if (core == null) return;
            if (core.getCallsNb() > 0) {
                Call call = core.getCurrentCall();
                if (call == null) {
                    // Current call can be null if paused for example
                    call = core.getCalls()[0];
                }
                if (call == null) return;
                call.removeListener(callListener);
                call.terminate();
            }
        } catch (Exception e) {

        }

    }


}
