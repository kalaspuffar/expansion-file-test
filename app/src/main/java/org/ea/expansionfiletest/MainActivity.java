package org.ea.expansionfiletest;

import android.Manifest;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.vending.expansion.downloader.DownloadProgressInfo;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import com.google.android.vending.expansion.downloader.DownloaderServiceMarshaller;
import com.google.android.vending.expansion.downloader.IDownloaderClient;
import com.google.android.vending.expansion.downloader.IDownloaderService;
import com.google.android.vending.expansion.downloader.IStub;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.APKExpansionPolicy;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.Messenger;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements IDownloaderClient {
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1337;
    private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh1qmkAXeeu1TGKeiVEnPd8/foZxCvA3FCRIwSRwdfJ2VdCgNG/rcs5oCmXvDiNVbDhr7Q/KRRAXCfScgxS3JBDPCGX1tUFcPP+op/OBjK6Hi5TlAFTVQA+pBGkvAplmJ14hjyxZwIJY9/7S5XFDh/8kdQrO1XRCxZPfirBbbl+pqAdhaC321Lprz2XTPmzDJRuiHGGHb5Z5M71vr7MjvVx+WD9oG7iK9chHtaLYcHtftwe81xBilMRspYQjtgx9xL5fiIUrFYywPH0aKjB+B9MyKqvW7uBJk0uxtgec8oe0jO/fK6sRrAeRzZnTqlhyls35VEQfpvoz/ii7i1kAAXwIDAQAB";
    private IDownloaderService remoteService;

    // Generate your own 20 random bytes, and put them here.
    private static final byte[] SALT = new byte[] {
            -46, 65, 30, -128, -103, -57, 74, -64, 51, 88, -95, -45, 77, -117, -36, -113, -11, 32, -64,
            89
    };

    private LicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;
    // A handler on the UI thread.
    private Handler mHandler;
    private IStub downloaderClientStub;

    private void downloadObbFile(File obbFile) {
        Log.w("MainActivity", "DOWNLOAD!");
    }

    private void playVideo(File obb) {
        VideoView videoview = findViewById(R.id.videoView2);
        Uri uri = Uri.fromFile(obb);
        videoview.setVideoURI(uri);
        videoview.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, "PERMISSION ACCEPTED", Toast.LENGTH_LONG);
                } else {
                    Toast.makeText(this, "PERMISSION DENIED", Toast.LENGTH_LONG);
                }
                return;
            }
        }
    }

    private void checkForVideo(File obb) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            if(obb.exists()) {
                playVideo(obb);
            } else {
                downloadObbFile(obb);
            }
        }
    }

    private boolean expansionFilesDelivered() {
        File obbDir = getObbDir();
        File obbFile = new File(obbDir, "main.1.org.ea.expansionfiletest.obb");
        return obbFile.exists();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File obbDir = getObbDir();
                File obbFile = new File(obbDir, "main.1.org.ea.expansionfiletest.obb");
                if(obbFile.exists()) {
                    checkForVideo(obbFile);
                } else {
                    downloadObbFile(obbFile);
                    checkForVideo(obbFile);
                }
            }
        });

        mHandler = new Handler();

        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // Library calls this when it's done.
        mLicenseCheckerCallback = new MyLicenseCheckerCallback();
        // Construct the LicenseChecker with a policy.
        mChecker = new LicenseChecker(
                this, new APKExpansionPolicy(this,
                new AESObfuscator(SALT, getPackageName(), deviceId)),
                BASE64_PUBLIC_KEY);

        //doCheck();

        try {
            // Check if expansion files are available before going any further
            if (!expansionFilesDelivered()) {
                // Build an Intent to start this activity from the Notification
                Intent notifierIntent = new Intent(this, MainActivity.class.getClass());
                notifierIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                        notifierIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                // Start the download service (if required)
                int startResult =
                        DownloaderClientMarshaller.startDownloadServiceIfRequired(this,
                                pendingIntent, SampleDownloaderService.class);
                // If download has started, initialize this activity to show
                // download progress
                if (startResult != DownloaderClientMarshaller.NO_DOWNLOAD_REQUIRED) {
                    // This is where you do set up to display the download
                    // progress (next step)
                    downloaderClientStub = DownloaderClientMarshaller.CreateStub(this,
                            SampleDownloaderService.class);

                    return;
                } // If the download wasn't necessary, fall through to start the app
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        if (null != downloaderClientStub) {
            downloaderClientStub.connect(this);
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        if (null != downloaderClientStub) {
            downloaderClientStub.disconnect(this);
        }
        super.onStop();
    }

    private void doCheck() {
        setProgressBarIndeterminateVisibility(true);
        mChecker.checkAccess(mLicenseCheckerCallback);
    }

    @Override
    public void onServiceConnected(Messenger m) {
        remoteService = DownloaderServiceMarshaller.CreateProxy(m);
        remoteService.onClientUpdated(downloaderClientStub.getMessenger());
    }

    @Override
    public void onDownloadStateChanged(int newState) {
        Log.w("DOWNLOAD", "Status " + newState);
    }

    @Override
    public void onDownloadProgress(DownloadProgressInfo progress) {
        Log.w("DOWNLOAD", "Progress " + progress.mOverallProgress);
    }


    private class MyLicenseCheckerCallback implements LicenseCheckerCallback {
        public void allow(int policyReason) {
            if (isFinishing()) {
                return;
            }

            displayResult("ALLOWED");
        }

        public void dontAllow(int policyReason) {
            if (isFinishing()) {
                return;
            }
            displayResult("NOT ALLOWED");
        }

        public void applicationError(int errorCode) {
            if (isFinishing()) {
                return;
            }

            String result = String.format("Application error %d", errorCode);
            displayResult(result);
        }
    }

    private void displayResult(final String result) {
        mHandler.post(new Runnable() {
            public void run() {
                Log.w("DISPLAY", result);
                setProgressBarIndeterminateVisibility(false);
                Toast.makeText(getApplication(), result, Toast.LENGTH_LONG);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mChecker.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
