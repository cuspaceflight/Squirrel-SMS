package uk.ac.cam.cusf.squirrelsms;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;

public class LocationUpdate extends Service {

    private static final String TAG = "SquirrelSMS";
    private static final int RETRY_DELAY = 20000;
    private static final int MAX_RETRY_ATTEMPTS = 4;

    private Handler mHandler;
    BroadcastReceiver sentReceiver;

    @Override
    public void onStart(Intent intent, int id) {

        Bundle extras = intent.getExtras();
        if (extras != null) {

            Log.i(TAG, "SMS info present");

            final String phoneNumber = extras.getString("phoneNumber");
            final String actionCode = extras.getString("actionCode");

            if (actionCode.toLowerCase().startsWith("locate")) {

                Log.i(TAG, "'locate' action code received");

                final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                Location location = locationManager
                        .getLastKnownLocation(LocationManager.GPS_PROVIDER);

                // If last known location was more than 30 seconds ago, register
                // to receive new update

                if (location == null
                        || System.currentTimeMillis() - location.getTime() > 1000 * 30) {

                    Log.i(TAG, "getLastKnownLocation was null or old");

                    LocationListener gpsListener = new LocationListener() {
                        public void onLocationChanged(Location location) {
                            Log.i(TAG, "onLocationChanged");
                            locationManager.removeUpdates(this);
                            sendSMS(phoneNumber, location, 0);
                        }

                        public void onProviderDisabled(String provider) {
                        }

                        public void onProviderEnabled(String provider) {
                        }

                        public void onStatusChanged(String provider,
                                int status, Bundle extras) {
                        }
                    };

                    locationManager
                            .requestLocationUpdates(
                                    LocationManager.GPS_PROVIDER, 10000, 0,
                                    gpsListener);

                } else {
                    sendSMS(phoneNumber, location, 0);
                }

            }

        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sentReceiver != null)
            unregisterReceiver(sentReceiver);
    }

    private void sendSMS(final String phoneNumber, final Location location,
            final int attempt) {

        Log.i(TAG, "Sending SMS");

        String SENT = "SMS_SENT";

        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(
                SENT), 0);

        // The retry code is untested on an actual device...
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                case Activity.RESULT_OK:
                    Log.i(TAG, "SMS sent: RESULT_OK");
                    stopSelf();
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Log.e(TAG, "SMS sent: RESULT_ERROR_GENERIC_FAILURE");
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Log.e(TAG, "SMS sent: RESULT_ERROR_NO_SERVICE");
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Log.e(TAG, "SMS sent: RESULT_ERROR_NULL_PDU");
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Log.e(TAG, "SMS sent: RESULT_ERROR_RADIO_OFF");
                default:
                    if (attempt <= MAX_RETRY_ATTEMPTS) {
                        if (mHandler == null)
                            mHandler = new Handler();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "Attempting to re-send SMS.");
                                sendSMS(phoneNumber, location, attempt + 1);
                            }
                        }, RETRY_DELAY);
                    } else {
                        Log.i(TAG, "Max retry attempts reached, giving up.");
                        stopSelf();
                    }
                    break;
                }
            }
        };

        registerReceiver(sentReceiver, new IntentFilter(SENT));

        String message = "latlong " + location.getLatitude() + ","
                + location.getLongitude() + "," + location.getAltitude() + ","
                + location.getAccuracy();

        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, sentPI, null);

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

}
