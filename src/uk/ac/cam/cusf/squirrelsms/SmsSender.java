package uk.ac.cam.cusf.squirrelsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;

public class SmsSender extends BroadcastReceiver {

    public final static String TAG = "SquirrelSMS";
    
    public final static String SMS_SEND = "uk.ac.cam.cusf.intent.SMS_SEND";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        
        Log.i(TAG, "SMS_SEND");
        
        if (intent.getAction().equals(SMS_SEND)) {
        
            String phoneNumber = intent.getExtras().getString("phoneNumber");
            String message = intent.getExtras().getString("message");
            
            if (phoneNumber != null && message != null) {
                Log.i(TAG, "Sending SMS to " + phoneNumber + ": " + message);
                SmsManager sms = SmsManager.getDefault();
                //sms.sendTextMessage(phoneNumber, null, message, null, null);
                
                Intent report = new Intent();
                report.setAction("uk.ac.cam.cusf.intent.Report");
                report.putExtra("message", message);
                context.sendBroadcast(report);
                
            } else {
                Log.e(TAG, "Intent received did not have valid phone number or message");
            }
        
        } else {
            Log.e(TAG, "Intent does not have correct action " + SMS_SEND
                    + ", received " + intent.getAction());
        }
        
    }

}
