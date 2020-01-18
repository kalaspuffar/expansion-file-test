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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements IDownloaderClient {
    private static final String FILENAME = "main." +BuildConfig.VERSION_CODE+ "." + BuildConfig.APPLICATION_ID + ".obb";
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1337;
    private IDownloaderService remoteService;
    private IStub downloaderClientStub;

    private TextView statusText;
    private ProgressBar progressBar;

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
        File obbFile = new File(obbDir, FILENAME);
        return obbFile.exists();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.textView);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(100);

        Button play_button = findViewById(R.id.play);
        play_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File obbDir = getObbDir();
                File obbFile = new File(obbDir, FILENAME);
                if(obbFile.exists()) {
                    checkForVideo(obbFile);
                } else {
                    downloadObbFile(obbFile);
                    checkForVideo(obbFile);
                }
            }
        });

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


    /**
     * Connect the stub to our service on start.
     */
    @Override
    protected void onStart() {
        if (null != downloaderClientStub) {
            downloaderClientStub.connect(this);
        }
        super.onStart();
    }

    /**
     * Disconnect the stub from our service on stop
     */
    @Override
    protected void onStop() {
        if (null != downloaderClientStub) {
            downloaderClientStub.disconnect(this);
        }
        super.onStop();
    }

    @Override
    public void onServiceConnected(Messenger m) {
        remoteService = DownloaderServiceMarshaller.CreateProxy(m);
        remoteService.onClientUpdated(downloaderClientStub.getMessenger());
    }

    @Override
    public void onDownloadStateChanged(int newState) {

        switch (newState) {
            case IDownloaderClient.STATE_IDLE:
                // STATE_IDLE means the service is listening, so it's
                // safe to start making calls via mRemoteService.
                statusText.setText("UDLE");
                break;
            case IDownloaderClient.STATE_CONNECTING:
                statusText.setText("CONNECTING");
                break;
            case IDownloaderClient.STATE_FETCHING_URL:
                statusText.setText("FETCHING URL");
                break;
            case IDownloaderClient.STATE_DOWNLOADING:
                statusText.setText( "DOWNLOADING");
                break;
            case IDownloaderClient.STATE_FAILED_CANCELED:
                statusText.setText("Canceled");
                break;
            case IDownloaderClient.STATE_FAILED:
                statusText.setText("Failed");
                break;
            case IDownloaderClient.STATE_FAILED_FETCHING_URL:
                statusText.setText("Failed fetch");
                break;
            case IDownloaderClient.STATE_FAILED_UNLICENSED:
                statusText.setText("Failed unlicensed");
                break;
            case IDownloaderClient.STATE_PAUSED_NEED_CELLULAR_PERMISSION:
                statusText.setText( "Need cellular permission");
                break;
            case IDownloaderClient.STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION:
                statusText.setText("Wifi disabled need cellular permission");
                break;
            case IDownloaderClient.STATE_PAUSED_BY_REQUEST:
                statusText.setText("Paused by request");
                break;
            case IDownloaderClient.STATE_PAUSED_ROAMING:
                statusText.setText("Paused roaming");
                break;
            case IDownloaderClient.STATE_PAUSED_SDCARD_UNAVAILABLE:
                statusText.setText("SDCard unavailable");
                break;
            case IDownloaderClient.STATE_COMPLETED:
                statusText.setText("State completed");
                return;
            default:
                statusText.setText("Unknown state");
        }
    }

    @Override
    public void onDownloadProgress(DownloadProgressInfo progress) {
        double progressDouble = (double)progress.mOverallProgress / 1520625009d * 100d;
        int progressVal = (int)progressDouble;
        Log.w("DOWNLOAD", "PROGRESS " + progressDouble);
        Log.w("DOWNLOAD", "PROGRESS " + progressVal);
        progressBar.setProgress(progressVal);
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
