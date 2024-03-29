package lava.retailcustomerclient.ui;


import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.liulishuo.filedownloader.BaseDownloadTask;

import java.util.ArrayList;

import lava.retailcustomerclient.R;
import lava.retailcustomerclient.services.APKInstallCheckService;
import lava.retailcustomerclient.utils.AppDownloader;
import lava.retailcustomerclient.utils.AppInfoObject;
import lava.retailcustomerclient.utils.AppsListToClientObject;
import lava.retailcustomerclient.utils.Constants;
import lava.retailcustomerclient.utils.GetAppsList;
import lava.retailcustomerclient.deviceutils.PhoneUtils;
import lava.retailcustomerclient.utils.NetworkUtils;
import lava.retailcustomerclient.utils.ProcessState;


public class CustomerKitActivity extends Activity implements AppDownloader.AppDownloadCallback {

    private AppsListToClientObject appsListObj;
    int downloadCount = 0;
    Button installButton;

    APKInstallCheckService apkInstallCheckService;
    final Messenger activityMessenger = new Messenger(new IncomingHandler());
    Messenger serviceMessenger = null;
    boolean mBound = false;
    public static final int MSG_UPDATE_UI = 1000;

    public static final int MSG_ASK_FOR_WIFI = 2001;
    public static int OVERLAY_PERMISSION_REQ_CODE = 1234;

    private Handler mHandler;



    void ShowToast (String text) {
        Toast.makeText(CustomerKitActivity.this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (appsListObj != null && appsListObj.appsList != null) {
            setGridAdapter();
        }

        // Bind to LocalService
        Intent intent = new Intent(this, APKInstallCheckService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE); // dont autocreate

        if (mBound) {
            // Call a method from the LocalService.
            // However, if this call were something that might hang, then this request should
            // occur in a separate thread to avoid slowing down the activity performance.
        }

        UpdateUI();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        //startService(new Intent(CustomerKitActivity.this, APKInstallCheckService.class));
        ProcessState.setState(ProcessState.STATE_NOT_STARTED);
        startProcess();
    }

    private void startProcess() {

        setContentView(R.layout.activity_first);

        if (PhoneUtils.isAccessibilityEnabled(this, Constants.accessibilityServiceId) == false) {

            TextView infoText = (TextView)findViewById(R.id.infoText);
            infoText.setText("Please enable [RetailJunction] service");

            Button button = (Button)findViewById(R.id.button);
            button.setText("Open Accessibility Setting -->");
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openAccessabilitySettings(v);
                    // // TODO: 5/24/2016 check if you can bring existing activity to top
                    finish();
                }
            });

        } else {

            // check if connected to right hotspot
            if (NetworkUtils.isConnectedToRetailWifi(getApplicationContext())) {

                ProcessState.setState(ProcessState.STATE_CONNECTED);
                setContentView(R.layout.activity_main);
                installButton = (Button) findViewById(R.id.doInstall);

                TextView statusText = (TextView)findViewById(R.id.statusText);
                statusText.setText("Connected to: " + NetworkUtils.getCurrentSSID(getApplicationContext()));

                fetchAppsList();

            } else {

                ProcessState.setState(ProcessState.STATE_NOT_STARTED);

                TextView infoText = (TextView)findViewById(R.id.infoText);
                infoText.setText("Not connected to " + Constants.wifiSSID);

                Button button = (Button)findViewById(R.id.button);
                button.setText("Retry");

                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startProcess();
                    }
                });

            }
        }
    }

    private void fetchAppsList() {

        // get apps list from Promoter's phone
        try {
            //.get makes it blocking
            // // TODO: 5/11/2016 make this unblocking. http error can cause UI hang
            ProcessState.setState(ProcessState.STATE_GETTING_APPSLIST);

            GetAppsList g = new GetAppsList(this);
            appsListObj = g.execute().get();

            // update grid
            if (appsListObj != null && appsListObj.appsList != null) {
                setGridAdapter();

                installButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doCompleteProcess();
                    }
                });

            } else {
                // no apps. // TODO: 5/11/2016 handle error
                ShowToast("No apps found");
            }
        } catch (Exception ee) {
            ProcessState.setState(ProcessState.STATE_CONNECTED);
            ShowToast("Failed to get appslist");
        }
    }


    public void openAccessabilitySettings(View view) {
        if (PhoneUtils.isAccessibilityEnabled(this, Constants.accessibilityServiceId) == false) {

            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, 0);
        }
    }

    public void setGridAdapter () {
        GridView gridview = (GridView) findViewById(R.id.appsgrid);
        gridview.setAdapter(new GridViewAdapter(CustomerKitActivity.this, getApplicationContext(), appsListObj.appsList));

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                Toast.makeText(CustomerKitActivity.this, "" + appsListObj.appsList.get(position).desc,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void doCompleteProcess() {

        if (checkPreConditions() == false) {
            return;
        }

        //reset it
        downloadCount = 0;

        //disable installButton
        installButton.setEnabled(false);

        //Step 1: Download Apps
        ProcessState.setState(ProcessState.STATE_DOWNLOADING_APKS);
        AppDownloader a = new AppDownloader(this);
        a.download(getApplicationContext().getFilesDir().getAbsolutePath(), appsListObj.appsList);
    }

    private boolean checkPreConditions() {

        if (NetworkUtils.isConnectedToRetailWifi(getApplicationContext()) == false) {
            ShowToast("Error: not connected to " + Constants.wifiSSID);
            return false;
        }

        if (checkSystemPermissions() == false) {
            return false;
        }

        if (PhoneUtils.getIMEI(getApplicationContext()) == null) {
            ShowToast("Error: unable to read IMEI");
            return false;
        }
        return true;
    }

    boolean checkSystemPermissions() {

        ArrayList<String> list = new ArrayList<String>();
        boolean ask = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Check if the READ_PHONE_STATE permission is already available.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                // READ_PHONE_STATE permission has not been granted.
                list.add(Manifest.permission.READ_PHONE_STATE);
                ask = true;
            }

            //java.io.IOException: Destination '/storage/emulated/0/AppsShare/temp/in.redbus.android.apk' directory cannot be created
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                ask = true;
            }

            if (ask == true) {
                requestPermissions(list.toArray(new String[0]), 0);
                return false;
            }

            if (!Settings.canDrawOverlays(this)) {
                requestDrawingPermission(this);
                return false;
            }
        }
        return true;
    }

    void requestDrawingPermission(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("Need drawing permission")
                .setMessage("Open drawing settings to allow?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    // SYSTEM_ALERT_WINDOW permission not granted...
                    ShowToast("Permission not granted");
                }
            }
        }
    }

    public void updateButtonText(String msg) {
        if (installButton != null) {
            installButton.setText(msg);
        }
    }


    @Override
    public void onApkDownloadCompleted(BaseDownloadTask task) {

        ((AppInfoObject)task.getTag()).downloadDone = true;
        downloadCount++;

        // check if all are downloaded
        if( downloadCount == appsListObj.appsList.size()) {
            ProcessState.setState(ProcessState.STATE_DONE_DOWNLOADING_APKS);
            ShowToast("All apk's downloaded.");


            // Bind to LocalService - AUTO CREATE
            Intent intent = new Intent(this, APKInstallCheckService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

            apkInstallCheckService.installApps(appsListObj);

            //enable installButton again when everything is done
            // // TODO: 5/26/2016 enable after all installs are done. not before. After enabling, change onClick()
        }
    }

    @Override
    public void onApkDownloadError(BaseDownloadTask task) {
        // // TODO: 5/11/2016 decide what to do
        ShowToast("Error downloading " + ((AppInfoObject)task.getTag()).appName);

    }

    @Override
    public void onApkDownloadProgress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
        updateButtonText("Downloading " + ((AppInfoObject)task.getTag()).appName + "..." + Integer.toString((soFarBytes*100/totalBytes)+1) + "%");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            APKInstallCheckService.LocalBinder binder = (APKInstallCheckService.LocalBinder) service;
            apkInstallCheckService = binder.getService();
            serviceMessenger = binder.getMessenger();
            mBound = true;

            try {
                Message msg = Message.obtain(null, APKInstallCheckService.MSG_REGISTER_CLIENT);
                msg.replyTo = activityMessenger;
                serviceMessenger.send(msg);
            }
            catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    class IncomingHandler extends Handler {
        @Override

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_UI:
                    UpdateUI();
                    break;

                case MSG_ASK_FOR_WIFI:
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {

                            if(!isFinishing())
                                showErrorDialog();

                        }
                    });
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }


    public void showErrorDialog() {

        // 1. Instantiate an AlertDialog.Builder with its constructor
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // 2. Chain together various setter methods to set the dialog characteristics
        builder.setMessage(R.string.report_not_submitted)
                .setTitle(R.string.retry);
        builder.setPositiveButton("Retry", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

                try {
                    Message msg = Message.obtain(null, APKInstallCheckService.MSG_RETRY_SUBMISSION);
                    msg.replyTo = activityMessenger;
                    serviceMessenger.send(msg);
                }
                catch (RemoteException e) {
                    // In this case the service has crashed before we could even do anything with it
                }

            }
        });

// 3. Get the AlertDialog from create()
        AlertDialog dialog = builder.create();

        dialog.show();



    }


    void UpdateUI() {
        int state = ProcessState.getState();

        switch(state) {
            case ProcessState.STATE_NOT_STARTED:
                break;
            case ProcessState.STATE_CONNECTED:
                break;
            case ProcessState.STATE_GETTING_APPSLIST:
                break;
            case ProcessState.STATE_DONE_GETTING_APPSLIST:
                break;
            case ProcessState.STATE_DOWNLOADING_APKS:
                break;
            case ProcessState.STATE_DONE_DOWNLOADING_APKS:
                break;
            case ProcessState.STATE_INSTALLING_APKS:
            case ProcessState.STATE_DONE_INSTALLING_APKS:
            case ProcessState.STATE_COLLECTING_DEVICE_DATA:
            case ProcessState.STATE_DONE_COLLECTING_DEVICE_DATA:
            case ProcessState.STATE_SUBMITTING_DATA:
                if (installButton != null)
                    installButton.setText("Installing APKs");
                break;
            case ProcessState.STATE_DONE_SUBMITTING_DATA:
                // update button onClick - uninstall
                if (installButton != null) {
                    installButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            uninstallSelf();
                        }
                    });
                    installButton.setText("Process complete. Press to remove kit & exit.");
                    installButton.setEnabled(true);
                }
                break;

            default:
                Log.e("UpdateUI", "Bad state "+state);
                break;
        }
    }

    void uninstallSelf() {
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:lava.retailcustomerclient"));
        startActivity(intent);


        Uri packageUri = Uri.parse("package:org.klnusbaum.test");
        Intent uninstallIntent =
                new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
        startActivity(uninstallIntent);
    }
}

