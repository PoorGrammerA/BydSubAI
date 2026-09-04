package com.poorgrammera.bydsubai.data;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


public class SavedDestination {
    private final int id;
    private final String alias;
    private final String address;
    private final double latitude;
    private final double longitude;

    public SavedDestination(int id, String alias, String address, double latitude, double longitude) {
        this.id = id;
        this.alias = alias;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getId() { return id; }
    public String getAlias() { return alias; }
    public String getAddress() { return address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
