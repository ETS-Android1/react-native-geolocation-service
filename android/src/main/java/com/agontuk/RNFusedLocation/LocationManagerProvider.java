package com.agontuk.RNFusedLocation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactApplicationContext;

import java.util.List;

public class LocationManagerProvider implements LocationProvider {
  private final LocationManager locationManager;

  private LocationChangeListener locationChangeListener;

  private boolean isSingleUpdate = false;
  private final LocationListener locationListener = new LocationListener() {
    @Override
    public void onLocationChanged(Location location) {
      locationChangeListener.onLocationChange(location);

      if (isSingleUpdate) {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        removeLocationUpdates();
      }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
      if (status == android.location.LocationProvider.AVAILABLE) {
        onProviderEnabled(provider);
      } else {
        onProviderDisabled(provider);
      }
    }

    @Override
    public void onProviderEnabled(String provider) {
      //
    }

    @Override
    public void onProviderDisabled(String provider) {
      locationChangeListener.onLocationError(LocationError.POSITION_UNAVAILABLE, null);
    }
  };
  private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
  private final Runnable timeoutRunnable = new Runnable() {
    @Override
    public void run() {
      locationChangeListener.onLocationError(LocationError.TIMEOUT, null);
      removeLocationUpdates();
    }
  };

  public LocationManagerProvider(ReactApplicationContext context) {
    this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
  }

  @SuppressLint("MissingPermission")
  @Override
  public void getCurrentLocation(LocationOptions locationOptions, LocationChangeListener locationChangeListener) {
    this.isSingleUpdate = true;
    this.locationChangeListener = locationChangeListener;
    String provider = getBestProvider(locationOptions.getAccuracy());

    if (provider == null) {
      locationChangeListener.onLocationError(LocationError.POSITION_UNAVAILABLE, null);
      return;
    }

    Location location = locationManager.getLastKnownLocation(provider);

    if (location != null &&
      (System.currentTimeMillis() - location.getTime()) < locationOptions.getMaximumAge()
    ) {
      Log.i(RNFusedLocationModule.TAG, "returning cached location.");
      locationChangeListener.onLocationChange(location);
      return;
    }

    startLocationUpdates(
      provider,
      locationOptions.getInterval(),
      locationOptions.getDistanceFilter(),
      locationOptions.getTimeout()
    );
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode) {
    return false;
  }

  @Override
  public void requestLocationUpdates(LocationOptions locationOptions, LocationChangeListener locationChangeListener) {
    this.isSingleUpdate = false;
    this.locationChangeListener = locationChangeListener;
    String provider = getBestProvider(locationOptions.getAccuracy());

    if (provider == null) {
      locationChangeListener.onLocationError(LocationError.POSITION_UNAVAILABLE, null);
      return;
    }

    startLocationUpdates(
      provider,
      locationOptions.getInterval(),
      locationOptions.getDistanceFilter(),
      locationOptions.getTimeout()
    );
  }

  @SuppressLint("MissingPermission")
  @Override
  public void removeLocationUpdates() {
    locationManager.removeUpdates(locationListener);
  }

  @Nullable
  private String getBestProvider(LocationAccuracy locationAccuracy) {
    Criteria criteria = getProviderCriteria(locationAccuracy);
    String provider = locationManager.getBestProvider(criteria, true);

    if (provider == null) {
      List<String> providers = locationManager.getProviders(true);
      provider = providers.size() > 0 ? providers.get(0) : null;
    }

    return provider;
  }

  private Criteria getProviderCriteria(LocationAccuracy locationAccuracy) {
    int accuracy;
    int baseAccuracy;
    int power;

    switch (locationAccuracy) {
      case high:
        accuracy = Criteria.ACCURACY_HIGH;
        baseAccuracy = Criteria.ACCURACY_FINE;
        power = Criteria.POWER_HIGH;
        break;
      case balanced:
        accuracy = Criteria.ACCURACY_MEDIUM;
        baseAccuracy = Criteria.ACCURACY_COARSE;
        power = Criteria.POWER_MEDIUM;
        break;
      case low:
        accuracy = Criteria.ACCURACY_LOW;
        baseAccuracy = Criteria.ACCURACY_COARSE;
        power = Criteria.POWER_LOW;
        break;
      case passive:
        accuracy = Criteria.NO_REQUIREMENT;
        baseAccuracy = Criteria.NO_REQUIREMENT;
        power = Criteria.NO_REQUIREMENT;
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + locationAccuracy);
    }

    Criteria criteria = new Criteria();
    criteria.setAccuracy(baseAccuracy);
    criteria.setBearingAccuracy(accuracy);
    criteria.setHorizontalAccuracy(accuracy);
    criteria.setPowerRequirement(power);
    criteria.setSpeedAccuracy(accuracy);
    criteria.setVerticalAccuracy(accuracy);

    return criteria;
  }

  @SuppressLint("MissingPermission")
  private void startLocationUpdates(String provider, long minTime, float minDistance, long timeout) {
    locationManager.requestLocationUpdates(
      provider,
      minTime,
      minDistance,
      locationListener,
      Looper.getMainLooper()
    );

    if (isSingleUpdate) {
      if (timeout > 0 && timeout != Long.MAX_VALUE) {
        timeoutHandler.postDelayed(timeoutRunnable, timeout);
      }
    }
  }
}
