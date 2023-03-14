package com.example.team30.DataCalculators;

import android.util.Pair;

import com.example.team30.models.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.Math;

public class Compass {
    private float myLong;

    private float myLat;
    public static final double EARTH_RADIUS = 6371; // in km
    public static final double MILES_PER_KM = 0.621371; // conversion factor from km to miles

    private Map<String, Pair<Float, Integer>> position;

    private static Compass instance;

    public static  Compass singleton(){
        if(instance == null){
            instance = new Compass();
        }
        return instance;
    }

    public Compass(){
        position = new HashMap<>();
    }

    public float getmyLong(){
        return myLong;
    }

    public float getMyLat(){
        return myLat;
    }

    public void setMyLong(float newLong){
        this.myLong = newLong;
    }

    public void  setMyLat(float newLat){
        this.myLat = newLat;
    }

    public void addPosition(String objectId, Pair<Float,Integer> newPair){
        position.put(objectId, newPair);
    }

    public Pair<Float, Integer> getPosition(String objectId){
        return position.get(objectId);
    }

    public void setPosition(String objectId, Pair<Float, Integer> newPair){
        position.put(objectId, newPair);
    }

    public void calculateAngles(List<Location> locationList, float orientation){
        for(Location location : locationList){
            String UID = location.getPublic_code();
            float longti = location.getLongitude();
            float lati = location.getLatitude();
            float y = longti - myLong;
            float x = lati - myLat;
            double angle = Math.atan(y/x) * 180/Math.PI;
            if(x < 0){
                angle = angle + 180;
            }
            if(x > 0 && y < 0){
                angle = angle + 360;
            }
            float newangle = (float)(angle - orientation);
            Pair<Float, Integer> newPair = new Pair<>(newangle, 100);

            position.put(UID, newPair);
        }
    }

    public float calculateAngle(float longti, float lati){
        float y = longti - myLong;
        float x = lati - myLat;
        double angle = Math.atan(y/x) * 180/Math.PI;
        if(x < 0){
            angle = angle + 180;
        }
        if(x > 0 && y < 0){
            angle = angle + 360;
        }
        float newangle = (float)(angle);
        return newangle;
    }

    public double calculateDistance(double friendLat, double friendLong) {
        double dLat = Math.toRadians(friendLat - myLat);
        double dLong = Math.toRadians(friendLong - myLong);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(myLat)) * Math.cos(Math.toRadians(friendLat)) *
                        Math.sin(dLong / 2) * Math.sin(dLong / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = EARTH_RADIUS * c;
        return distance * MILES_PER_KM;
    }

}