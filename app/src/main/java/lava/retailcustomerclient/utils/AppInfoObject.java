package lava.retailcustomerclient.utils;

import java.util.Date;

/**
 * Created by Mridul on 4/5/2016.
 */

/*
 *   ************************************************************
 *   ******************       WARNING            ****************
 *   ************************************************************
 *
 *   ENSURE THIS CLASS IS EXACTLY SAME IN RETAIL JUNCTION PROJECT
 *
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
    public int minsdk;
    public String category;
    public String desc;
    public float rating;

    /* Fields for client */
    public boolean downloadDone; //used by client
    public int installDone; //used by client
    public long installts; //used by client
}



