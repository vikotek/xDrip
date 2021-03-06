package com.eveningoutpost.dexdrip.Services;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;

import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.UtilityModels.MongoSendTask;
import com.eveningoutpost.dexdrip.xdrip;

import static com.eveningoutpost.dexdrip.UtilityModels.Constants.SYNC_QUEUE_RETRY_ID;

public class SyncService extends IntentService {
    private static final String TAG = "SyncService";

    public SyncService() {
        super("SyncService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "STARTING INTENT SERVICE");
        attemptSend();
    }

    private void attemptSend() {
        if (Home.getPreferencesBooleanDefaultFalse("cloud_storage_api_enable")
                || Home.getPreferencesBooleanDefaultFalse("wear_sync")
                || Home.getPreferencesBooleanDefaultFalse("cloud_storage_mongodb_enable")
                || Home.getPreferencesBooleanDefaultFalse("cloud_storage_influxdb_enable")) {
            synctoCloudDatabases(); // attempt to sync queues
            startSyncService(6 * Constants.MINUTE_IN_MS); // set retry timer
        }
    }

    // TODO Refactor MongoSendTask name
    private void synctoCloudDatabases() {
        final MongoSendTask task = new MongoSendTask(getApplicationContext());
        task.executeOnExecutor(xdrip.executor);
    }

    public static void startSyncService(long delay) {
        Log.d("SyncService", "static starting Sync service delay: " + delay);
        if (delay == 0) {
            xdrip.getAppContext().startService(new Intent(xdrip.getAppContext(), SyncService.class));
        } else {
            final PendingIntent serviceIntent = PendingIntent.getService(xdrip.getAppContext(), SYNC_QUEUE_RETRY_ID, new Intent(xdrip.getAppContext(), SyncService.class), PendingIntent.FLAG_CANCEL_CURRENT);
            JoH.wakeUpIntent(xdrip.getAppContext(), delay, serviceIntent);
        }
    }
}
