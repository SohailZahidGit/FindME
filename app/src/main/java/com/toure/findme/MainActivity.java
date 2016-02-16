package com.toure.findme;

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.util.Date;



public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    public static final String LOG_TAG = MainActivity.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;

    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";

    // Bool to track whether the app is already resolving an error(Google play error)
    private boolean mResolvingError = false;

    private static final String STATE_RESOLVING_ERROR = "resolving_error";

    //Holds the device location
    Location mLastLocation;

    //Location update request
    LocationRequest mLocationRequest;

    //Keep record of the last time the location was available
    String mLastUpdateTime;

    MainActivityFragment MF;

    int cnt = 0;

    //Broadcast receiver for location change
    BroadcastReceiver locRecievr;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);



        //Get the state of error resolving tracker
        mResolvingError = (savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false));

        //Create the Google ApiClient instance service
        buildGoogleApiClient();
        Log.d(LOG_TAG, "Initialised the API Client");

        setContentView(R.layout.activity_main);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //THis method build the Google API Client which will be used to get services
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "the API Client is connected");

        LocationAvailability la = LocationServices.FusedLocationApi.getLocationAvailability(mGoogleApiClient);
        Log.d(LOG_TAG, Boolean.toString(la.isLocationAvailable()));
        Log.d(LOG_TAG, la.toString());

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation != null) {
            try{
                MF = (MainActivityFragment) getSupportFragmentManager().findFragmentById(R.id.locationFragment);

                MF.setLocation(mLastLocation);
                MF.updateUI();
                Log.d(LOG_TAG, "new location:  " + Integer.toString(++cnt) + " times");

            }
            catch(NullPointerException e){
                Log.d(LOG_TAG, "Null pointer exception1:  " + e.getMessage()+
                        "frag: " + MF.toString());

                e.getStackTrace();
            }


        }
        //Request for location updates

        startLocationUpdates();

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, " the API Client connection failed");

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        }
        else if (connectionResult.hasResolution()){
            mResolvingError = true;
            try {
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
                e.printStackTrace();
            }
        }
        else{
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            showErrorDialog(connectionResult.getErrorCode());
            mResolvingError = true;

        }

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, " API Client connection suspended");
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.

    }

    @Override
    protected void onResume() {
        super.onResume();

        int errorCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);


        if(errorCode != ConnectionResult.SUCCESS){
            Dialog errorDialog =  GooglePlayServicesUtil.getErrorDialog(errorCode, this, REQUEST_RESOLVE_ERROR,
                    new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            // Leave if services are unavailable
                            finish();
                        }
                    });

            errorDialog.show();
        }

        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        //Check if the device has the Google play Service APK install
        //It's only if this is available that we can connect the API client

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(resultCode == ConnectionResult.SUCCESS){
            //Here, we are sure that th device has the Google play Service APK
            if (!mResolvingError) {
                Log.d(LOG_TAG," API Client connecting");
                mGoogleApiClient.connect();

            }
        }
        else{
            //Get the error dialog and prompt the user for solving the corresponding error
            GooglePlayServicesUtil.getErrorDialog(resultCode, this, REQUEST_RESOLVE_ERROR).show();
        }

    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect(); //disconnect the google play services before closing the app
        Log.d(LOG_TAG, " API Client disconnected");
        super.onStop();

    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MainActivity)getActivity()).onDialogDismissed();
        }
    }

    //This method is called after a GoogleAPi connection  problem is solved
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            }
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000); // 10 secs
        mLocationRequest.setFastestInterval(5000); // 5 secs
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void startLocationUpdates() {
        createLocationRequest();
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

        LocationUpdateReciever locReciever = new LocationUpdateReciever(MF);

        Intent intent = new Intent(this, LocationService.class);

        PendingIntent pi = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, pi);
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

        try{
            MF = (MainActivityFragment) getSupportFragmentManager().findFragmentById(R.id.locationFragment);

            MF.setLocation(mLastLocation);
            MF.updateUI();
            Log.d(LOG_TAG, "new location:  " + Integer.toString(++cnt) + " times");
        }
        catch(NullPointerException e){
            Log.d(LOG_TAG, "Null pointer exception2:  " + e.getMessage()+
                    "frag: " + MF.toString());
            e.getStackTrace();
        }

    }



    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    protected void stopLocationUpdates() {
        if(mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }

    //Getter method for the API Client
    public GoogleApiClient getApiClient(){
        return mGoogleApiClient;
    }
}
