package uk.ac.cam.cusf.squirrelsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SquirrelSMS";

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i(TAG, "Message received");

        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs = null;

        if (bundle != null) {

            Object[] pdus = (Object[]) bundle.get("pdus");
            msgs = new SmsMessage[pdus.length];
            for (int i = 0; i < msgs.length; i++) {
                msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);

                Intent updateIntent = new Intent(context, LocationUpdate.class);
                updateIntent.putExtra("phoneNumber", msgs[i]
                        .getOriginatingAddress());
                updateIntent.putExtra("actionCode", msgs[i].getMessageBody()
                        .toString());
                context.startService(updateIntent);
                Log.i(TAG, "LocationUpdate started");
            }

        }
    }

}