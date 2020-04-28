package com.iisfree.linephone;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.LogCollectionState;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;


public class LinePhoneService extends Service {
    private static final String START_LINPHONE_LOGS = " ==== Device information dump ====";
    // Keep a static reference to the Service so we can access it from anywhere in the app
    private static LinePhoneService sInstance;
    private static final String TAG = "LinphoneService";
    private String mBasePath;

    private Handler mHandler;
    private Timer mTimer;

    private Core mCore;
    private CoreListenerStub mCoreListener;
    private CallRegister callRegister;

    public interface CallRegister {
        void isRegister(boolean isOk);
    }

    public void setRegisterCallBack(CallRegister callRegister) {
        this.callRegister = callRegister;
    }


    public static boolean isReady() {
        return sInstance != null;
    }

    public static LinePhoneService getInstance() {
        return sInstance;
    }

    public static Core getCore() {
        if (isReady()) return sInstance.mCore;
        return null;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBasePath = getExternalCacheDir().getAbsolutePath();
        // The first call to liblinphone SDK MUST BE to a Factory method
        // So let's enable the library debug logs & log collection
        String basePath = mBasePath;
        Factory.instance().setLogCollectionPath(basePath);
        Factory.instance().enableLogCollection(LogCollectionState.Enabled);
        Factory.instance().setDebugMode(true, getString(R.string.app_name));
        // Dump some useful information about the device we're running on
        Log.i(START_LINPHONE_LOGS);
        dumpDeviceInformation();
        dumpInstalledLinphoneInformation();

        mHandler = new Handler();
        // This will be our main Core listener, it will change activities depending on events
        mCoreListener = new CoreListenerStub() {

            @Override
            public void onRegistrationStateChanged(Core core, ProxyConfig cfg, RegistrationState cstate, String message) {
                super.onRegistrationStateChanged(core, cfg, cstate, message);
                Log.d("onRegistrationStateChanged: " + cstate);
                if (callRegister != null) {
                    callRegister.isRegister(cstate == RegistrationState.Ok);
                }

                try {
                    android.util.Log.d(TAG, "onRegistrationStateChanged: " + cfg.findAuthInfo().getUsername() + cfg.getState());
                    boolean defaultAccountConnected =
                            (core != null
                                    && core.getDefaultProxyConfig() != null
                                    && core.getDefaultProxyConfig().getState() == RegistrationState.Ok);
                    // ToastUtils.showShort(defaultAccountConnected ? "通话服务注册成功" : "通话服务注册失败");


                } catch (Exception e) {
                    Log.e(e);
                }
            }


            @Override
            public void onCallStateChanged(Core core, Call call, Call.State state, String message) {
                if (state == Call.State.IncomingReceived) {
                    if (call != null) {
                        //sip:17200001001@192.168.0.208 单元门口机
                        //sip:17217091001@192.168.0.208 室内机
                        String userName = call.getRemoteAddress().getUsername();
                        String toAddress = call.getToAddress().getUsername();
                        Toast.makeText(getBaseContext(),userName+"来电",Toast.LENGTH_SHORT).show();

                    }
                } else if (state == Call.State.End || state == Call.State.Released) {
                    // Once call is finished (end state), terminate the activity
                    // We also check for released state (called a few seconds later) just in case
                    // we missed the first one

                }


            }
        };

        try {
            // Let's copy some RAW resources to the device
            // The default config file must only be installed once (the first time)
            copyIfNotExist(R.raw.linphonerc_default, basePath + "/.linphonerc.txt");
            File file = new File(basePath + "/ring.wav");
            if (!file.exists()) {
                file.delete();
            }
            //复制assets目录铃声到SD卡
            new CopyZipFileToSD(this, "ring.wav", basePath).copy();
            // The factory config is used to override any other setting, let's copy it each time
            copyFromPackage(R.raw.linphonerc_factory, "linphonerc");
            // Create the Core and add our listener
            mCore = Factory.instance()
                    .createCore(basePath + "/.linphonerc", basePath + "/linphonerc", this);
            //铃声必须16位 wav格式
            mCore.setRing(basePath + "/ring.wav");
            //设置最大通话数
            mCore.setMaxCalls(1);
            //设置接听超时60s
            mCore.setIncTimeout(60);
            //监听
            mCore.addListener(mCoreListener);
            mCore.enableKeepAlive(true);
            // Core is ready to be configured
            configureCore();
        } catch (Exception exp) {

        }

    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // If our Service is already running, no need to continue
        if (sInstance != null) {
            return START_NOT_STICKY;
        }

        // Our Service has been started, we can keep our reference on it
        // From now one the Launcher will be able to call onServiceReady()
        sInstance = this;

        // Core must be started after being created and configured
        mCore.start();
        // We also MUST call the iterate() method of the Core on a regular basis
        TimerTask lTask =
                new TimerTask() {
                    @Override
                    public void run() {
                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mCore != null) {
                                            mCore.iterate();
                                        }
                                    }
                                });
                    }
                };
        mTimer = new Timer("Linphone scheduler");
        mTimer.schedule(lTask, 0, 20);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mCore.removeListener(mCoreListener);
        mTimer.cancel();
        // A stopped Core can be started again
        mCore.stop();
        // To ensure resources are freed, we must ensure it will be garbage collected
        mCore = null;
        // Don't forget to free the singleton as well
        sInstance = null;

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // For this sample we will kill the Service at the same time we kill the app
        stopSelf();

        super.onTaskRemoved(rootIntent);
    }

    private void configureCore() {
        // We will create a directory for user signed certificates if needed
        String basePath = getFilesDir().getAbsolutePath();
        String userCerts = basePath + "/user-certs";
        File f = new File(userCerts);
        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.e(userCerts + " can't be created.");
            }
        }
        mCore.setUserCertificatesPath(userCerts);
    }

    private void dumpDeviceInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("DEVICE=").append(Build.DEVICE).append("\n");
        sb.append("MODEL=").append(Build.MODEL).append("\n");
        sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
        sb.append("SDK=").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Supported ABIs=");
        for (String abi : Version.getCpuAbis()) {
            sb.append(abi).append(", ");
        }
        sb.append("\n");
        Log.i(sb.toString());
    }

    private void dumpInstalledLinphoneInformation() {
        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(nnfe);
        }

        if (info != null) {
            Log.i(
                    "[Service] Linphone version is ",
                    info.versionName + " (" + info.versionCode + ")");
        } else {
            Log.i("[Service] Linphone version is unknown");
        }
    }

    private void copyIfNotExist(int ressourceId, String target) throws IOException {
        File lFileToCopy = new File(target);
        if (!lFileToCopy.exists()) {
            copyFromPackage(ressourceId, lFileToCopy.getName());
        }
    }

    private void copyFromPackage(int ressourceId, String target) throws IOException {
        FileOutputStream lOutputStream = openFileOutput(target, 0);
        InputStream lInputStream = getResources().openRawResource(ressourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = lInputStream.read(buff)) != -1) {
            lOutputStream.write(buff, 0, readByte);
        }
        lOutputStream.flush();
        lOutputStream.close();
        lInputStream.close();
    }
}
