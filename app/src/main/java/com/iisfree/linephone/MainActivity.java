package com.iisfree.linephone;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;

import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TextureView textureView;
    private EditText etUser, etSip, etPwd, etTo;
    private static final String TAG = "SIPTEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.videoSurface);
        etUser = findViewById(R.id.et_user);
        etSip = findViewById(R.id.et_sip);
        etPwd = findViewById(R.id.et_pwd);
        etTo = findViewById(R.id.et_to);
        AndPermission.with(this).runtime().permission(
                new String[]{Manifest.permission.RECORD_AUDIO, // 音频
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA}).onGranted(new Action<List<String>>() {
            @Override
            public void onAction(List<String> permissions) {
                Log.d(TAG, "onAction: ");
                startService(new Intent(MainActivity.this, LinePhoneService.class));
                etUser.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        LinePhoneService.getCore().addListener(new CoreListenerStub() {
                            @Override
                            public void onCallStateChanged(Core core, Call call, Call.State state, String message) {
                                Toast.makeText(MainActivity.this,state.toString(),Toast.LENGTH_SHORT).show();
                                Log.d(TAG, "onCallStateChanged: "+state);
                                if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
                                    // Once call is finished (end state), terminate the activity
                                    // We also check for released state (called a few seconds later) just in case
                                    // we missed the first one
                                    Log.d(TAG, "onCallStateChanged: 通话结束");

                                }
                            }
                        });
                    }
                }, 3000);
            }
        }).start();

    }

    public void acceptCall(View view) {
        CallManager.getInstance().acceptVideoCall();
        Core core = LinePhoneService.getCore();
        core.setNativeVideoWindowId(textureView);
    }

    public void handUp(View view) {
        CallManager.getInstance().handUp();
    }

    public void callNum(View view) {
        CallManager.getInstance().callPhone(etTo.getText().toString());
    }

    public void register(View view) {
        String userName = etUser.getText().toString();
        String pwd = etPwd.getText().toString();
        String sipServer = etSip.getText().toString();
        CallManager.getInstance().registerUser(sipServer, userName, pwd);

    }
}
