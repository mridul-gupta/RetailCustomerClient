package lava.retailcustomerclient.utils;

import java.util.Date;

/**
 * Created by Mridul on 4/5/2016.
 */
public class AppInfoObject {

    public long campaignId;
    public String appName;
    public String packageName;
    public String iconUrl;
    public String apkUrl;
    public String version;
    public String checksum;
    public int size; //bytes

    /* Fields for client */
    public boolean downloadDone; //used by client
    public int installDone; //used by client
    public long installts; //used by client
}



