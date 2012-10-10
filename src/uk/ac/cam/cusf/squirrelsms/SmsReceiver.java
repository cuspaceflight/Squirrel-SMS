package uk.ac.cam.cusf.squirrelsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SquirrelSMS";

    private final static String SMS_RECEIVED = "uk.ac.cam.cusf.intent.SMS_RECEIVED";
    
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

                String phoneNumber = msgs[i].getOriginatingAddress();
                String message = msgs[i].getMessageBody().toString();
                int spacePos = message.indexOf(" ");
                String actionCode;
                if (spacePos >= 0) {
                    actionCode = message.substring(0, spacePos);
                } else {
                    actionCode = message;
                }
                
                if (actionCode.toLowerCase().startsWith("locate")) {
                
                    Intent updateIntent = new Intent(context, LocationUpdate.class);
                    updateIntent.putExtra("phoneNumber", phoneNumber);
                    updateIntent.putExtra("actionCode", actionCode);
                    context.startService(updateIntent);
                    Log.i(TAG, "LocationUpdate started");
                    
                } else {
                    
                    boolean valid = false;
                    String[] allowed = new String[]{
                            "start",
                            "stop",
                            "status"
                    };

                    for (String keyword : allowed) {
                        valid |= actionCode.equals(keyword);
                    }
                    
                    if (valid) {
                        Intent textIntent = new Intent();
                        textIntent.setAction(SMS_RECEIVED);
                        textIntent.putExtra("phoneNumber", phoneNumber);
                        textIntent.putExtra("command", actionCode);
                        context.sendBroadcast(textIntent);
                    }
                    
                }
            }

        }
    }

}