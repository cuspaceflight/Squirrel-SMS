package uk.ac.cam.cusf.squirrelsms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

public class SquirrelSMS extends MapActivity {

    public static final String TAG = "SquirrelSMS";

    MapView mapView;
    MapController mc;
    BroadcastReceiver sentReceiver;
    ReverseGeocode reverseGeocode;
    MyLocationOverlay myLocation;

    private boolean showMyLocation = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        reverseGeocode = new ReverseGeocode();

        mapView = (MapView) findViewById(R.id.mapview1);
        mapView.setBuiltInZoomControls(true);
        mapView.setReticleDrawMode(MapView.ReticleDrawMode.DRAW_RETICLE_NEVER);
        mc = mapView.getController();

        myLocation = new MyLocationOverlay(this, mapView);
        myLocation.disableMyLocation();
        myLocation.disableCompass();

        Intent mapIntent = getIntent();

        if (mapIntent != null && mapIntent.getExtras() != null
                && mapIntent.getExtras().get("lat") != null) {
            onNewIntent(mapIntent);
        } else {
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            if (prefs.contains("time")) {
                mapIntent.putExtra("lat", prefs.getFloat("lat", 0));
                mapIntent.putExtra("long", prefs.getFloat("long", 0));
                mapIntent.putExtra("accuracy", prefs.getFloat("accuracy", 0));
                mapIntent.putExtra("mobile", prefs.getString("mobile", ""));
                mapIntent.putExtra("time", prefs.getLong("time", 0));
                onNewIntent(mapIntent);
            }
        }

    }

    @Override
    public void onNewIntent(Intent mapIntent) {

        if (mapIntent != null && mapIntent.getExtras() != null
                && mapIntent.getExtras().get("lat") != null) {
            GeoPoint p = new GeoPoint((int) ((Float) mapIntent.getExtras().get(
                    "lat") * 1E6), (int) ((Float) mapIntent.getExtras().get(
                    "long") * 1E6));
            mc.animateTo(p);
            mc.setZoom(18);

            Drawable marker = getResources().getDrawable(
                    R.drawable.ic_maps_indicator_current_position);
            marker.setBounds(0, 0, marker.getIntrinsicWidth(), marker
                    .getIntrinsicHeight());

            AccuracyOverlay accuracyOverlay = new AccuracyOverlay(
                    (Float) mapIntent.getExtras().get("accuracy"), p);
            LocationOverlay locationOverlay = new LocationOverlay(this, marker,
                    p.getLatitudeE6(), p.getLongitudeE6(), mapIntent
                            .getExtras());

            mapView.getOverlays().clear();
            if (showMyLocation)
                mapView.getOverlays().add(myLocation);
            mapView.getOverlays().add(accuracyOverlay);
            mapView.getOverlays().add(locationOverlay);
            mapView.invalidate();

            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong("time", (Long) mapIntent.getExtras().get("time"));
            editor.putFloat("lat", (Float) mapIntent.getExtras().get("lat"));
            editor.putFloat("long", (Float) mapIntent.getExtras().get("long"));
            editor.putFloat("accuracy", (Float) mapIntent.getExtras().get(
                    "accuracy"));
            editor.putString("mobile", (String) mapIntent.getExtras().get(
                    "mobile"));
            editor.commit();

            reverseGeocode.execute(p);

        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sentReceiver != null)
            unregisterReceiver(sentReceiver);
        myLocation.disableMyLocation();
        myLocation.disableCompass();
    }

    private void sendSMS(String phoneNumber) {

        String SENT = "SMS_SENT";
        String message = "locate";

        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(
                SENT), 0);

        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    alertMessage("SMS Error", "RESULT_ERROR_GENERIC_FAILURE");
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    alertMessage("SMS Error", "RESULT_ERROR_NO_SERVICE");
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    alertMessage("SMS Error", "RESULT_ERROR_NULL_PDU");
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    alertMessage("SMS Error", "RESULT_ERROR_RADIO_OFF");
                    break;
                }
            }
        };

        registerReceiver(sentReceiver, new IntentFilter(SENT));

        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, sentPI, null);

    }

    private void alertMessage(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialoginterface, int i) {
                    }
                }).show();
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.locate:
            inputNumber();
            return true;
        case R.id.tracker:
            if (showMyLocation) {
                mapView.getOverlays().remove(myLocation);
                myLocation.disableMyLocation();
                myLocation.disableCompass();
            } else {
                myLocation.enableMyLocation();
                myLocation.enableCompass();
                mapView.getOverlays().add(myLocation);
            }
            mapView.invalidate();
            showMyLocation = !showMyLocation;
            return true;
        case R.id.satellite:
            mapView.setSatellite(!mapView.isSatellite());
            return true;
        case R.id.clear:
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong("time", 0);
            editor.commit();
            mapView.getOverlays().clear();
            mapView.invalidate();
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void inputNumber() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Mobile number");

        final EditText input = new EditText(this);
        input.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setKeyListener(new DialerKeyListener());

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        input.setText(prefs.getString("mobile", null));

        final LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setPadding(10, 0, 10, 0);
        linearLayout.addView(input);

        alert.setView(linearLayout);

        alert.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String value = input.getText().toString();
                        sendSMS(value);
                    }
                });

        alert.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                });

        alert.show();

    }

    class AccuracyOverlay extends Overlay {

        private GeoPoint position;
        private float accuracy;

        public AccuracyOverlay(float accuracy, GeoPoint position) {
            super();
            this.accuracy = accuracy;
            this.position = position;
        }

        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            Projection p = mapView.getProjection();
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            paint.setARGB(40, 255, 0, 0);
            paint.setAntiAlias(true);

            Point point = p.toPixels(position, null);
            float equatorPixels = p.metersToEquatorPixels(accuracy);
            float pixels = (float) (equatorPixels / Math.cos(Math
                    .toRadians(position.getLatitudeE6() * 1E-6)));
            canvas.drawCircle(point.x, point.y, pixels, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setARGB(80, 255, 0, 0);
            canvas.drawCircle(point.x, point.y, pixels, paint);

        }

    }

    class LocationOverlay extends ItemizedOverlay<OverlayItem> {

        private List<OverlayItem> locations = new ArrayList<OverlayItem>();
        private Context mContext;
        private Drawable marker;

        public LocationOverlay(Context context, Drawable defaultMarker,
                int LatitudeE6, int LongitudeE6, Bundle extras) {
            super(defaultMarker);
            this.mContext = context;
            this.marker = defaultMarker;
            GeoPoint myPlace = new GeoPoint(LatitudeE6, LongitudeE6);
            Date date = new Date((Long) extras.getLong("time"));
            String snippet = "%s";
            snippet += "Latitude: " + String.format("%.6f", LatitudeE6 * 1E-6);
            snippet += "\nLongitude: "
                    + String.format("%.6f", LongitudeE6 * 1E-6);
            snippet += "\n\n" + "Accuracy: "
                    + String.format("%.1f", extras.getFloat("accuracy"))
                    + " metres";
            snippet += "\n\n" + "(" + date.toLocaleString() + ")";
            locations.add(new OverlayItem(myPlace, (String) extras
                    .getString("mobile"), snippet));
            populate();
        }

        @Override
        protected OverlayItem createItem(int i) {
            return locations.get(i);
        }

        @Override
        public int size() {
            return locations.size();
        }

        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            if (!shadow)
                super.draw(canvas, mapView, shadow);
            boundCenter(marker);
        }

        @Override
        protected boolean onTap(int index) {

            String location = "";
            if (reverseGeocode.getStatus() == AsyncTask.Status.FINISHED) {
                try {
                    Log.i(TAG, "Status.FINISHED");
                    Address address = reverseGeocode.get();
                    if (address != null) {
                        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                            location += address.getAddressLine(i);
                            if (i != address.getMaxAddressLineIndex())
                                location += ", ";
                        }
                        location += "\n\n";
                    }
                } catch (Exception e) {
                }
            }

            OverlayItem item = locations.get(index);
            AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
            dialog.setTitle(item.getTitle());
            dialog.setMessage(String.format(item.getSnippet(), location));
            dialog.show();
            return true;
        }
    }

    private class ReverseGeocode extends AsyncTask<GeoPoint, Void, Address> {

        @Override
        protected Address doInBackground(GeoPoint... params) {

            double latitude = params[0].getLatitudeE6() * 1E-6;
            double longitude = params[0].getLongitudeE6() * 1E-6;

            Geocoder myLocation = new Geocoder(getApplicationContext(), Locale
                    .getDefault());
            List<Address> address;
            try {
                address = myLocation.getFromLocation(latitude, longitude, 1);
            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
                return null;
            }

            return address.get(0);
        }

        @Override
        protected void onPostExecute(Address result) {
            Log.i(SquirrelSMS.TAG, result.toString());
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

}