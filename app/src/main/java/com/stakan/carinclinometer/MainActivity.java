package com.stakan.carinclinometer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {
    private static final int LOCATION_REQUEST = 42;
    private SensorManager sensors;
    private Sensor rotationSensor;
    private LocationManager locations;
    private DashboardView dashboard;
    private final float[] matrix = new float[9];
    private final float[] orientation = new float[3];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        dashboard = new DashboardView(this);
        setContentView(dashboard);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationSensor == null) rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        locations = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        } else startLocation();
    }

    @Override protected void onResume() {
        super.onResume();
        if (rotationSensor != null) sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override protected void onPause() {
        super.onPause();
        sensors.unregisterListener(this);
        locations.removeUpdates(this);
    }

    private void startLocation() {
        try {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0.5f, this);
            locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500, 2f, this);
            Location last = locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) onLocationChanged(last);
        } catch (SecurityException ignored) { }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startLocation();
        else dashboard.setGpsDenied(true);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        float heading = (float) Math.toDegrees(orientation[0]);
        if (heading < 0) heading += 360f;
        // Landscape dashboard mapping verified on the mounted phone.
        float roll = (float) Math.toDegrees(orientation[1]);
        float pitch = (float) Math.toDegrees(orientation[2]);
        dashboard.updateAttitude(roll, pitch, heading);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public void onLocationChanged(Location location) {
        float speed = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        dashboard.updateLocation(location.getLatitude(), location.getLongitude(), speed,
                location.hasAltitude() ? location.getAltitude() : 0, location.getAccuracy());
    }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { dashboard.setGpsAvailable(false); }
}
