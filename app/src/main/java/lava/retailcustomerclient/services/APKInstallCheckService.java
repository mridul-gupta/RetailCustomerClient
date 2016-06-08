package lava.retailcustomerclient.services;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import lava.retailcustomerclient.R;
import lava.retailcustomerclient.deviceutils.PhoneUtils;
import lava.retailcustomerclient.utils.AppInfoObject;
import lava.retailcustomerclient.utils.PromoterInfoObject;
import lava.retailcustomerclient.utils.SubmitData;
import lava.retailcustomerclient.utils.SubmitDataObject;

//import com.github.lzyzsd.circleprogress.ArcProgress;

/**
 * Created by Mridul on 4/12/2016.
 */
public class APKInstallCheckService extends Service {
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    static WindowManager wm;
    static View mView;
    static int nextIndex;

    LayoutInflater inflate;
    static Context serviceContext;

    static private List<AppInfoObject> installList;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceContext = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            onApkInstallDone(intent.getStringExtra("installed_package"));
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public APKInstallCheckService getService() {
            // Return this instance of LocalService so clients can call public methods
            return APKInstallCheckService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public void onDestroy() {

        super.onDestroy();
    }

    /** method for clients */
    public SubmitDataObject getStatus() {
        return null;
    }

    void startOverlay() {

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        Display display = wm.getDefaultDisplay(); // get phone display size
        int width = display.getWidth();  // deprecated - get phone display width
        int height = display.getHeight(); // deprecated - get phone display height


        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);


        params.gravity = Gravity.LEFT | Gravity.CENTER;

        inflate = (LayoutInflater) getBaseContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mView = inflate.inflate(R.layout.progress_overlay, null);

        if (mView != null) {
            ImageView cancelImage = (ImageView) mView.findViewById(R.id.cancelImage);

            cancelImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopOverlay();
                }
            });

            updateOverlay();

            wm.addView(mView, params);
        }
    }

    static void stopOverlay() {
        if(mView != null) {
            wm.removeView(mView);
            mView = null;
        }

        nextIndex = 0;
    }

    static void updateOverlay() {
        if (mView != null) {
            TextView textLabel = (TextView) mView.findViewById(R.id.appdetail);
            if (textLabel != null) {
                textLabel.setText("Installing: " + installList.get(nextIndex).appName + "\n(" + (nextIndex+1) + "/" + installList.size() + ")");
            }
        }
    }

    public void installApps(List<AppInfoObject> installList) {

        this.installList = installList;
        if (installList == null) {
            Log.d("installApps", "Starting app installation");
            return;
        }
        Log.d("installApps", "Starting app installation");

        startOverlay();

        // start Installs
        // reset static variables
        nextIndex = 0;

        continueInstallApps();
    }

    private void continueInstallApps() {

        String apkInternalPath = getApplicationContext().getFilesDir().getAbsolutePath() + "/apks/";
        String apkExternalPath = Environment.getExternalStorageDirectory() + "/AppsShare/temp/";

        File file = new File(apkExternalPath);
        file.mkdirs(); // ensure directory is present

        //Copy file to external memory first
        String fromFileName = apkInternalPath + installList.get(nextIndex).packageName + ".apk";
        String toFileName = apkExternalPath + installList.get(nextIndex).packageName + ".apk";

        File origFile = new File(fromFileName);
        File tempFile = new File(toFileName);

        try {
            FileUtils.copyFile(origFile, tempFile);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

            // make sure file permissions are set
            tempFile.setReadable(true, false);

            ComponentName c;

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(tempFile), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // without this flag android returned a intent error!
            //intent.setComponent(new ComponentName(activityContext.getPackageName(), "AppInstaller"));
            //intent.setPackage(activityContext.getApplicationContext().getPackageName());
            startActivity(intent);
        }
    }

    public void onApkInstallDone(String packageName) {

        Log.e("Service", "Installed: " + packageName);
        if (installList != null) {

            installList.get(nextIndex).installDone = 1; // installed
            installList.get(nextIndex).installts = System.currentTimeMillis();

            nextIndex++;

            // all apps done?
            if (nextIndex >= installList.size()) {
                stopOverlay();

                //1: reset static data
                nextIndex = 0;

                //2: inform UI.


                //3: Collect Installation & Device data
                SubmitData s = new SubmitData(serviceContext);
                s.execute(getSubmitDataObject());

                //4: Submit data to promoter
            } else {

                continueInstallApps();
                updateOverlay();
            }
        }
    }

    static SubmitDataObject getSubmitDataObject() {

        SubmitDataObject data = new SubmitDataObject();

        // fill blank promoter info -- will be overwritten by promoter
        data.promoterInfo = new PromoterInfoObject();
        data.promoterInfo.promoterId = null; // just to be safe
        data.promoterInfo.imei = null;
        data.promoterInfo.android_id = null;
        data.promoterInfo.model = null;
        data.promoterInfo.shareAppVersionCode = 0; // just to be safe
        data.promoterInfo.shareAppVersionName = null; // just to be safe

        data.deviceDetails = PhoneUtils.getDeviceInfo(serviceContext);

        data.installRecords = installList;

        return data;
    }
}
