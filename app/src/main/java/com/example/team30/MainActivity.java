package com.example.team30;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.team30.DataCalculators.Compass;
import com.example.team30.DataCalculators.LocationService;
import com.example.team30.DataCalculators.OrientationService;
import com.example.team30.models.API;

import com.example.team30.models.Friend;
import com.example.team30.models.FriendDao;

import com.example.team30.models.Location;
import com.example.team30.models.MainViewModel;
import com.example.team30.models.Repository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private OrientationService orientationService;
    private LocationService locationService;
    private ExecutorService backgroundThreadExecutor = Executors.newSingleThreadExecutor();
    private Future<Void> future;
    private Compass compass;
    private ConstraintLayout circular_constraint;
    private List<Friend> friendList;
    private LiveData<List<Location>> locations;
    private long lastLocationTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences data = getSharedPreferences("test", MODE_PRIVATE);
        SharedPreferences.Editor editor = data.edit();

        if(data.getBoolean("register", false) == false){
            setContentView(R.layout.activity_main);
            Intent intent = new Intent(MainActivity.this, RegistrationActivity.class);
            startActivity(intent);
        }

        //zoom level 1 is the most zoomed-out
        //zoom level 3 is the most zoomed-in
        if(data.getInt("zoom level", -1) == 1) {
            setContentView(R.layout.activity_level1);
            Button zoomOut = findViewById(R.id.zoom_out);
            //zoomOut.setClickable(false);
            //zoomOut.setAlpha(0.5f);
        }
        else if (data.getInt("zoom level", -1) == 2) {
            setContentView(R.layout.activity_main);
        }
        else if (data.getInt("zoom level", -1) == 3) {
            setContentView(R.layout.activity_level3);
            Button zoomIn = findViewById(R.id.zoom_in);
            zoomIn.setClickable(false);
            zoomIn.setAlpha(0.5f);
        }
        else if (data.getInt("zoom level", -1) == 0) {
            setContentView(R.layout.activity_level0);
            Button zoomIn = findViewById(R.id.zoom_in);
            Button zoomOut = findViewById(R.id.zoom_out);
            zoomOut.setAlpha(0.5f);
            zoomIn.setClickable(false);
        }

        if(data.getInt("zoom level", -1) < 3) {
            Button zoomIn = findViewById(R.id.zoom_in);
            zoomIn.setOnClickListener(v -> {
                Log.d("MainActivity", "zoom in clicked");
                int currZoom = data.getInt("zoom level", -1);
                editor.putInt("zoom level", currZoom + 1);
                editor.apply();
                recreate();
            });
        }

        if(data.getInt("zoom level", -1) > 0) {
            Button zoomOut = findViewById(R.id.zoom_out);
            zoomOut.setOnClickListener(v -> {
                Log.d("MainActivity", "zoom out clicked");
                int currZoom = data.getInt("zoom level", -1);
                editor.putInt("zoom level", currZoom - 1);
                editor.apply();
                recreate();
            });
        }

        // Check for and get location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 20-1);
        }
        lastLocationTime = System.currentTimeMillis();
        compass = Compass.singleton();
        locationService = LocationService.singleton(this);
        orientationService = OrientationService.singleton(this);
        circular_constraint = findViewById(R.id.compass1);
        TextView redDot = findViewById(R.id.RecDot);
        TextView greenDot = findViewById(R.id.GreenDot);
        TextView timer = findViewById(R.id.timer);

        MainViewModel viewModel = setupViewModel();

        locationService.getLocation().observe(this, coords ->{
            lastLocationTime = System.currentTimeMillis();
            redDot.setVisibility(View.INVISIBLE);
            timer.setVisibility(View.INVISIBLE);
            greenDot.setVisibility(View.VISIBLE);
            viewModel.updateUserLocation(data.getString("YourUID", ""), data.getString("privateCode", ""), coords);
            compass.setCoords(coords);
            System.out.println(coords);
        });
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            long currTime = System.currentTimeMillis();
            float lostTimeInMin = (float)(currTime - lastLocationTime)/60000;
            //lost signal time greater than 1 min
            System.out.println("Lost time (m)" + String.valueOf(lostTimeInMin) + "Last Update: " + String.valueOf(lastLocationTime));
            if (lostTimeInMin >= 1){
                String label = String.valueOf((int)lostTimeInMin) + "m";
                //UIThread of change GPS lost signal UI
                runOnUiThread(()->{
                    timer.setText(label);
                    redDot.setVisibility(View.VISIBLE);
                    timer.setVisibility(View.VISIBLE);
                    greenDot.setVisibility(View.INVISIBLE);
                });
            }
        }, 0, 1, TimeUnit.MINUTES);

        orientationService.getOrientation().observe(this, angle->{
            float degrees = (float) (angle * 180/Math.PI);
            compass.setMyAngle(degrees);
        });

        locations = viewModel.getLocations();
        locations.observe(this, this::onChanged);
    }

    public void onChanged(List<Location> locations) {
        //System.out.println("updated locations");
        SharedPreferences data = getSharedPreferences("test", MODE_PRIVATE);
        if(locations != null){
            for(Location location: locations){
                addDotToLayout(this,location, circular_constraint, data);
            }
        }
    }

    public void addFriend(View view) {
        Intent intent = new Intent(MainActivity.this, AddFriendActivity.class);
        startActivity(intent);
        finish();
    }

    private void addDotToLayout(Context context, Location location, ConstraintLayout layout, SharedPreferences data) {
        ImageView dot = layout.findViewWithTag(location.getPublic_code());
        boolean newDot = false;
        if(dot == null){
            newDot = true;
            dot = new ImageView(this);
        }
        dot.setImageResource(R.drawable.dot);
        dot.setTag(location.getPublic_code());
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );

        float density = context.getResources().getDisplayMetrics().density;

        params.dimensionRatio = "1:1";
        params.height = 50;
        params.width = 50;
        params.circleConstraint = R.id.compass1;
        params.circleAngle = compass.calculateAngle(location.getLatitude(), location.getLongitude());
        var distance = compass.calculateDistance(location.getLongitude(), location.getLatitude());

        if(data.getInt("zoom level", -1) == 2 ) {
            params.circleRadius = (int)(compass.zoom2radius(distance)*density);
            if(distance < 10){
                dot.setVisibility(View.INVISIBLE);
            }
        }
        else if(data.getInt("zoom level", -1) == 1 ) {
            params.circleRadius = (int)(compass.zoom1radius(distance)*density);
            if(distance < 500){
                dot.setVisibility(View.INVISIBLE);
            }
        }
        else if(data.getInt("zoom level", -1) == 3 ){
            params.circleRadius = (int)(compass.zoom3radius(distance)*density);
            if(distance < 1 ){
                dot.setVisibility(View.INVISIBLE);
            }
        }
        else if(data.getInt("zoom level", -1) == 0 ){
            params.circleRadius = (int)(compass.zoom0radius(distance)*density);
                dot.setVisibility(View.INVISIBLE);
        }
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        System.out.println(distance);
        System.out.println(density);
        System.out.println(params.circleRadius);

        dot.setLayoutParams(params);
        if(newDot){
            dot.setId(View.generateViewId());
            layout.addView(dot);
            if(dot.getVisibility() == View.INVISIBLE){
                addLabelToLayout(location.getLabel(), layout, dot);
            }
        }
    }

    private void addLabelToLayout(String label, ConstraintLayout layout, ImageView dot) {
        TextView textView = new TextView(this);
        textView.setId(View.generateViewId());
        textView.setText(label);
        textView.setTextColor(Color.BLACK);
        textView.setBackgroundColor(Color.WHITE);

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );

        params.topToBottom = dot.getId();
        params.startToStart = dot.getId();
        params.endToEnd = dot.getId();
        textView.setLayoutParams(params);

        layout.addView(textView);
    }

    private MainViewModel setupViewModel() {
        return new ViewModelProvider(this).get(MainViewModel.class);
    }
}
