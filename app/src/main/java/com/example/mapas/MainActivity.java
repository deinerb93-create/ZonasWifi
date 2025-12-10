package com.example.mapas;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private Marker userMarker, destinationMarker;
    private Polyline routePolyline;

    private TextView txtDistance;

    private static final int REQUEST_LOCATION = 100;

    LatLng destination = new LatLng(4.638193, -74.084046);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng zipaquira = new LatLng(5.0231, -74.0048);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(zipaquira, 13));
        loadDatasetMarkers();
    }

    private void checkPermissionsAndStartLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION
            );
        } else {
            startLocationUpdates();

        }
    }

    private void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(30000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateUserLocation(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                getMainLooper()
        );
    }

    private void updateUserLocation(Location location) {

       LatLng userLatLng = new LatLng(4.639535058203513,-74.07446514987647);

        if (userMarker != null) userMarker.remove();

        userMarker = mMap.addMarker(new MarkerOptions()
                .position(userLatLng)
                .title("Tú")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        mMap.animateCamera(CameraUpdateFactory.newLatLng(userLatLng));

        drawRoute(userLatLng, destination);
    }
    private void loadDatasetMarkers() {

        String url = "https://www.datos.gov.co/resource/2seq-65bi.json";

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                String json = response.body().string();
                JSONArray array = new JSONArray(json);

                for (int i = 0; i < array.length(); i++) {

                    JSONObject obj = array.getJSONObject(i);
                    String barrio = obj.optString("barrios", "Zona Wifi");
                    JSONObject geo = obj.getJSONObject("geocoded_column");
                    JSONArray coords = geo.getJSONArray("coordinates");

                    double lon = coords.getDouble(0);
                    double lat = coords.getDouble(1);

                    LatLng position = new LatLng(lat, lon);

                    runOnUiThread(() -> {
                        mMap.addMarker(new MarkerOptions()
                                .position(position)
                                .title(barrio)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        );
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void drawRoute(LatLng origin, LatLng destination) {
        String url = "https://maps.googleapis.com/maps/api/directions/json?"
                + "origin=" + origin.latitude + "," + origin.longitude
                + "&destination=" + destination.latitude + "," + destination.longitude
                + "&key="+ getString(R.string.google_maps_key);
        Log.d("ruta", "drawRoute: "+url);

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();

                String json = response.body().string();
                JSONObject jsonObject = new JSONObject(json);

                JSONArray routes = jsonObject.getJSONArray("routes");
                JSONObject route = routes.getJSONObject(0);
                JSONObject leg = route.getJSONArray("legs").getJSONObject(0);

                String distance = leg.getJSONObject("distance").getString("text");

                String polyline = route.getJSONObject("overview_polyline").getString("points");
                List<LatLng> points = decodePolyline(polyline);

                runOnUiThread(() -> {
                    Toast.makeText(getApplicationContext(),url,Toast.LENGTH_LONG).show();
                    Toast.makeText(getApplicationContext(),"Distancia =" + distance, Toast.LENGTH_SHORT ).show();
                    txtDistance.setText("Distancia: " + distance);

                    if (routePolyline != null) routePolyline.remove();

                    routePolyline = mMap.addPolyline(
                            new PolylineOptions()
                                    .addAll(points)
                                    .width(10)
                                    .color(0xFF0099FF)
                                    .geodesic(true)
                    );
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();

        int index = 0, len = encoded.length(), lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;

            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;

            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng point = new LatLng((lat / 1E5), (lng / 1E5));
            poly.add(point);
        }

        return poly;
    }
}
