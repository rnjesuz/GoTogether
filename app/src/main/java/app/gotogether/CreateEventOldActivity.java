package app.gotogether;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;


import java.io.IOException;
import java.util.List;
import java.util.Locale;

import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableLayout;
import expandablelib.gotogether.Section;

import static android.support.constraint.Constraints.TAG;

public class CreateEventOldActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    /** Id to identify a location permission request. */
    private static final int REQUEST_LOCATION = 0;
    private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    /** Permissions required to use device location. */
    private static String[] PERMISSIONS_LOCATION = {Manifest.permission.ACCESS_FINE_LOCATION};
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    /** Provider of the user geolocation */
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private Location currentLocation = null;
    private String parents = "Do you volunteer as a Driver?";
    private String destination = null;
    private String start = null;
    private boolean isDriver = false;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats
    private boolean mRequestingLocationUpdates = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event_old);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close); // change the return to parent button


        // Initialize both fragment (Start and Destination queries) for Google's Places API
        initializePlaceAutoCompleteFragments();
        // Initialize an ExpandableLayout that opens if user wants to volunteer as driver
        initializeExpandableLayout();
        // Set location service provider
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Build location request parameters
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds

        FloatingActionButton completeEvent = (FloatingActionButton) findViewById(R.id.create_done);
        completeEvent.setShowAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_scale_up));
        completeEvent.setHideAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_scale_down));
        int delay = 1000;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                completeEvent.show(true);
            }
        }, delay);
    }

    /** Build callback for new location request
     *  After a location is found it's translated into an address and written on the respective AutoComplete Fragment
     * @param location it's used to differentiate between a destination request and a start request*/
    private void buildLocationCallBack(String location){
        mLocationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                //currentLocation = (Location) locationResult.getLastLocation();
                for (Location location: locationResult.getLocations()) {
                    currentLocation = location;
                }

                // Start or destination request?
                if(location.equals("start"))
                    handleNewLocation(currentLocation, "start");
                else if (location.equals("destination"))
                    handleNewLocation(currentLocation, "destination");

                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            }};
    }

    private void initializePlaceAutoCompleteFragments() {

        // Create bounds to Portugal
        LatLngBounds portugalBounds = new LatLngBounds(new LatLng(32.2895, -31.4648), new LatLng(42.154311, -6.189159));

        //the fragment for the destination
        PlaceAutocompleteFragment destinationAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.destination_autocomplete_fragment_create);
        destinationAutocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination = place.getAddress().toString();
                Log.i(TAG, "Place - destination: " + place.getAddress().toString());
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });
        // Set bias using bounds
        destinationAutocompleteFragment.setBoundsBias(portugalBounds);

        //the fragment for the starting location
        PlaceAutocompleteFragment startAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.start_autocomplete_fragment_create);
        startAutocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                start = place.getAddress().toString();
                Log.i(TAG, "Place - start: " + place.getAddress().toString());
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });
        // Set bias using bounds
        startAutocompleteFragment.setBoundsBias(portugalBounds);
    }

    private void initializeExpandableLayout() {
        // get the layout
        ExpandableLayout sectionLinearLayout = (ExpandableLayout) findViewById(R.id.el);
        // set renderers for parent and child views
        sectionLinearLayout.setRenderer(new ExpandableLayout.Renderer<Driver, SeatNumber>() {
            @Override
            public void renderParent(View view, Driver model, boolean isExpanded, int parentPosition) {
                ((TextView) view.findViewById(R.id.tvParent)).setText(model.name);
                view.findViewById(R.id.checkbox).setBackgroundResource(isExpanded ? R.drawable.checkbox_fill : R.drawable.checkbox_outlined);
            }

            @Override
            public void renderChild(View view, SeatNumber model, int parentPosition, int childPosition) {
                ((TextView) view.findViewById(R.id.tvChild)).setText(model.name);
            }
        });

        // create wanted sections - one, to ask for dricving possibility
        sectionLinearLayout.addSection(getSection());

        //create listeners for expansion or collapse of the layout
        sectionLinearLayout.setExpandListener((ExpandCollapseListener.ExpandListener<Driver>) (parentIndex, parent, view) -> {
            // layour expanded = volunteering for driving
            isDriver = true;
        });
        sectionLinearLayout.setCollapseListener((ExpandCollapseListener.CollapseListener<Driver>) (parentIndex, parent, view) -> {
            // layout collapsed = don't want to be driver
            isDriver = false;
            emptySeats = -1;
        });
    }

    /** Create a section to be displayed in the ExpandableLayout */
    public Section<Driver, SeatNumber> getSection() {
        Section<Driver, SeatNumber> Section = new Section<>();
        Driver Driver = new Driver(parents);
        SeatNumber seat = new SeatNumber();

        Section.parent = Driver;
        Section.children.add(seat);
        Section.expanded = false;
        return Section;
    }

    /**
     Finalize event creation.
     Validate activity fields.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void concludeCreation(View view) {
        // Has destination?
        if (destination == null) {
            // Send error message
            createTextPopUpWindow("The event's destination cannot be empty.");
            return;
        }
        // Has pick-up location?
        if (start == null) {
            // Send error message
            createTextPopUpWindow("Your pick-up location cannot be empty.");
            return;
        }
        // Volunteered as driver?
        EditText seatsText = findViewById(R.id.tvChild);
        boolean unspecifiedSeats = TextUtils.isEmpty(seatsText.getText());
        if (isDriver) {
            if (unspecifiedSeats) {
                // Didn't specify seats, use Hint value
                emptySeats = Integer.parseInt(seatsText.getHint().toString());
            } else {
                // Use inputted values
                emptySeats = Integer.parseInt(seatsText.getText().toString());
            }
        }
        // Confirm creation
        createConfirmationPopUpWindow();
    }

    private void createConfirmationPopUpWindow() {
        ConstraintLayout cConstraintLayout = (ConstraintLayout) findViewById(R.id.cl);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.confirmation_pop_up_window, null);

                /*
                    public PopupWindow (View contentView, int width, int height)
                        Create a new non focusable popup window which can display the contentView.
                        The dimension of the window must be passed to this constructor.

                        The popup does not provide any background. This should be handled by
                        the content view.

                    Parameters
                        contentView : the popup's content
                        width : the popup's width
                        height : the popup's height
                */
        // Initialize a new instance of popup window
        cPopupWindow = new PopupWindow(
                customView,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                true
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            cPopupWindow.setElevation(5.0f);
        }

        // Get a reference for the custom view text
        TextView messageText = (TextView) customView.findViewById(R.id.tv);
        // Set text
        if (isDriver) {
            String htmlMsg = "<b>Event destination: </b><br />" + destination + "<br /><b>Your pick-up location: </b><br />" + start + "<br /><b>Driver?</b><br />Yes<br /><b>Available seats: </b><br />" + emptySeats;
            Spanned spanned = Html.fromHtml(htmlMsg);
            SpannableString spannableString = new SpannableString(spanned);
            messageText.setText(spannableString);
        } else {
            String htmlMsg = "<b>Event destination: </b><br />" + destination + "<br /><b>Your pick-up location: </b><br />" + start + "<br /><b>Driver?</b><br />No";
            messageText.setText(Html.fromHtml(htmlMsg));
        }

        // Get a reference for the custom view confirm button
        Button confirmButton = (Button) customView.findViewById(R.id.ib_confirm);
        // Set a click listener for the popup window confirm button
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                cPopupWindow.dismiss();
                // Confirm Event
                CreateEventOldActivity.this.confirmEvent();

            }
        });

        // Get a reference for the custom view cancel button
        Button cancelButton = (Button) customView.findViewById(R.id.ib_cancel);
        // Set a click listener for the popup window cancel button
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                cPopupWindow.dismiss();
            }
        });

                /*
                    public void showAtLocation (View parent, int gravity, int x, int y)
                        Display the content view in a popup window at the specified location. If the
                        popup window cannot fit on screen, it will be clipped.
                        Learn WindowManager.LayoutParams for more information on how gravity and the x
                        and y parameters are related. Specifying a gravity of NO_GRAVITY is similar
                        to specifying Gravity.LEFT | Gravity.TOP.

                    Parameters
                        parent : a parent view to get the getWindowToken() token from
                        gravity : the gravity which controls the placement of the popup window
                        x : the popup's x location offset
                        y : the popup's y location offset
                */

        // Detect a click outside the window - Dismiss is the default behaviour of outside click
        cPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
            }
        });

        // Finally, show the popup window at the center location of root relative layout
        cPopupWindow.showAtLocation(cConstraintLayout, Gravity.CENTER, 0, 0);

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);
    }

    public void createTextPopUpWindow(String message) {
        ConstraintLayout tConstraintLayout = (ConstraintLayout) findViewById(R.id.cl);
        PopupWindow tPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.simple_text_pop_up_window, null);

                /*
                    public PopupWindow (View contentView, int width, int height)
                        Create a new non focusable popup window which can display the contentView.
                        The dimension of the window must be passed to this constructor.

                        The popup does not provide any background. This should be handled by
                        the content view.

                    Parameters
                        contentView : the popup's content
                        width : the popup's width
                        height : the popup's height
                */
        // Initialize a new instance of popup window
        tPopupWindow = new PopupWindow(
                customView,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                true
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            tPopupWindow.setElevation(5.0f);
        }

        // Get a reference for the custom view text
        TextView messageText = (TextView) customView.findViewById(R.id.tv);

        // Set text
        messageText.setText(message);

        // Get a reference for the custom view close button
        ImageButton closeButton = (ImageButton) customView.findViewById(R.id.ib_close);

        // Set a click listener for the popup window close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Restore activity to opaque
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
                // Dismiss the popup window
                tPopupWindow.dismiss();
            }
        });

                /*
                    public void showAtLocation (View parent, int gravity, int x, int y)
                        Display the content view in a popup window at the specified location. If the
                        popup window cannot fit on screen, it will be clipped.
                        Learn WindowManager.LayoutParams for more information on how gravity and the x
                        and y parameters are related. Specifying a gravity of NO_GRAVITY is similar
                        to specifying Gravity.LEFT | Gravity.TOP.

                    Parameters
                        parent : a parent view to get the getWindowToken() token from
                        gravity : the gravity which controls the placement of the popup window
                        x : the popup's x location offset
                        y : the popup's y location offset


                */


        // Detect a click outside the window - Dismiss is the default behaviour of outside click
        tPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
            }
        });

        // Finally, show the popup window at the center location of root relative layout
        tPopupWindow.showAtLocation(tConstraintLayout, Gravity.CENTER, 0, 0);

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);
    }

    /** Apply dim to the activity */
    public void applyDim(@NonNull ViewGroup parent, float dimAmount) {
        Drawable dim = new ColorDrawable(Color.BLACK);
        dim.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        dim.setAlpha((int) (255 * dimAmount));

        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.add(dim);
    }

    /** Clear dim from the activity */
    public void clearDim(@NonNull ViewGroup parent) {
        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.clear();
    }


    public void confirmEvent() {
        Log.d("Confirmation", "Yup");
        //Talk with server
        //TODO send info. receive token
        //TODO make pop-up to confirm input

        //Launch new activity
        // TODO do split? if finalized : if not finalized - different activities based on each
        Intent intent = new Intent(CreateEventOldActivity.this, EventActivity.class);
        // Destination Bundle
        Bundle destinationBundle = new Bundle();
        LatLng destinationLatLng = getLocationFromAddress(getApplicationContext(), destination);
        destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
        destinationBundle.putString("destinationAddress", destination);
        intent.putExtra("Destination", destinationBundle);
        // User Bundle
        Bundle userBundle = new Bundle();
        LatLng startLatLng = getLocationFromAddress(getApplicationContext(), start);
        User user;
        if (isDriver) {
            user = new User("teste","Ricardo", start, startLatLng, emptySeats);
        } else {
            user = new User("teste","Ricardo", start, startLatLng);
        }
        userBundle.putParcelable("User", user);
        intent.putExtra("Participants", userBundle);
        // Start
        startActivity(intent);
    }

    /** Get latitude and longitude from the address*/
    public LatLng getLocationFromAddress(Context context, String strAddress) {

        Geocoder coder = new Geocoder(context);
        List<Address> address;
        LatLng p1 = null;

        try {
            // May throw an IOException
            address = coder.getFromLocationName(strAddress, 5);
            //TODO resolver caso do oceano, ou propriedade privada sem estrada -> Nenhum emdereço será retornado
            if (address == null) {
                return null;
            }

            Address location = address.get(0);
            p1 = new LatLng(location.getLatitude(), location.getLongitude());

        } catch (IOException ex) {

            ex.printStackTrace();
        }

        return p1;
    }

    public void setMyLocationDestination(View view) {
        Log.i(TAG, "My location (destination) button pressed. Checking permission.");
        // BEGIN_INCLUDE(location_permission)
        // Check if the Location permission is already available.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Location permission has not been granted
            requestLocationPermission(view);
        } else {
            // Location permissions is already available, use last known location.
            Log.i(TAG, "LOCATION permission has already been granted.");
            // Check for GPS service
            if (checkGPS()) {
                Log.i("Set My Location", "Destination - Getting new location");
                buildLocationCallBack("destination");
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null );

                /*Log.i(TAG, "GPS is turned on. Getting last known location");
                mFusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                            @SuppressLint("MissingPermission")
                            @Override
                            public void onSuccess(Location location) {
                                // Got last known location. In some rare situations this can be null.
                                if (location != null) {
                                    Log.i("Set My Location", "Destination - Using last known location");
                                    handleNewLocation(location, "destination");

                                } else {
                                    Log.i("Set My Location", "Destination - Getting new location");
                                    buildLocationCallBack("destination");
                                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
                                }
                            }
                        });*/
            }
        }

    }

    public void setMyLocationStart(View view) {
        Log.i(TAG, "My location button (start) pressed. Checking permission.");
        // BEGIN_INCLUDE(location_permission)
        // Check if the Location permission is already available.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Location permission has not been granted
            requestLocationPermission(view);
        } else {
            // Location permissions is already available, use last known location.
            Log.i(TAG, "LOCATION permission has already been granted. Using last know location.");
            if (checkGPS()) {
                Log.i("Set My Location", "Start - Getting new location");
                buildLocationCallBack("start");
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);

                /*Log.i(TAG, "GPS is turned on. Getting last known location");
                mFusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                            @SuppressLint("MissingPermission")
                            @Override
                            public void onSuccess(Location location) {
                                // Got last known location. In some rare situations this can be null.
                                if (location != null) {
                                    Log.i("Set My Location", "Start - Using last known location");
                                    handleNewLocation(location, "start");

                                } else {
                                    Log.i("Set My Location", "Start - Getting new location");
                                    buildLocationCallBack("start");
                                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
                                }
                            }
                        });*/
            }
        }

    }

    private void handleNewLocation(Location location, String splitter) {
        if (splitter.equals("start")) {
            String locationAddress = getCompleteAddressString(location.getLatitude(), location.getLongitude());
            start = locationAddress;
            PlaceAutocompleteFragment startAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.start_autocomplete_fragment_create);
            startAutocompleteFragment.setText(start);
        }
        if (splitter.equals("destination")) {
            String locationAddress = getCompleteAddressString(location.getLatitude(), location.getLongitude());
            destination = locationAddress;
            PlaceAutocompleteFragment destinationAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.destination_autocomplete_fragment_create);
            destinationAutocompleteFragment.setText(destination);
        }
    }

    private boolean checkGPS() {
        boolean gpsEnabled = false;
        final LocationManager manager = (LocationManager) CreateEventOldActivity.this.getSystemService(Context.LOCATION_SERVICE);

        if (!hasGPSDevice(CreateEventOldActivity.this)) {
            Log.e("GPS", "Gps not supported");
            Toast.makeText(CreateEventOldActivity.this, "Gps not Supported", Toast.LENGTH_SHORT).show();
            gpsEnabled = false;
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(CreateEventOldActivity.this)) {
            Log.e("GPS", "Gps not enabled");
            enableLoc();
            gpsEnabled = false;
        } else {
            Log.i("GPS", "Gps is enabled");
            gpsEnabled = true;
        }
        return gpsEnabled;
    }

    private boolean hasGPSDevice(Context context) {
        final LocationManager mgr = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);
        if (mgr == null)
            return false;
        final List<String> providers = mgr.getAllProviders();
        if (providers == null)
            return false;
        return providers.contains(LocationManager.GPS_PROVIDER);
    }

    private void enableLoc() {
        mGoogleApiClient = new GoogleApiClient.Builder(CreateEventOldActivity.this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {

                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            mGoogleApiClient.connect();
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {

                            Log.d("Location error", "Location error " + connectionResult.getErrorCode());
                        }
                    }).build();
            mGoogleApiClient.connect();

            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(5 * 1000);
            locationRequest.setFastestInterval(2 * 1000);
            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest);

            builder.setAlwaysShow(true);

            PendingResult<LocationSettingsResult> result =
                    LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
            result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(LocationSettingsResult result) {
                    final Status status = result.getStatus();
                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                status.startResolutionForResult(CreateEventOldActivity.this, REQUEST_LOCATION);

                                //finish();
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            }
                            break;
                    }
                }
            });
    }

    private void requestLocationPermission(View view) {
        Log.i(TAG, "LOCATION permission has NOT been granted. Requesting permission.");

        // BEGIN_INCLUDE(location_permission_request)
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // For example if the user has previously denied the permission.
            Log.i(TAG, "Displaying location permission rationale to provide additional context.");
            Snackbar.make(view, R.string.permission_location_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(CreateEventOldActivity.this, PERMISSIONS_LOCATION, REQUEST_LOCATION);
                        }
                    })
                    .show();
        } else {
            // Location permission has not been granted yet. Request it directly.
            ActivityCompat.requestPermissions(this, PERMISSIONS_LOCATION, REQUEST_LOCATION);
        }
        // END_INCLUDE(location_permission_request)
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_LOCATION) {
            Log.i(TAG, "Received response for location permissions request.");

            // Received permission result
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // All required permissions have been granted.
                Snackbar.make(findViewById(R.id.cl), R.string.permission_available_location,
                        Snackbar.LENGTH_SHORT)
                        .show();
            } else {
                Log.i(TAG, "Location permissions were NOT granted.");
                Snackbar.make(findViewById(R.id.cl), R.string.permissions_not_granted,
                        Snackbar.LENGTH_SHORT)
                        .show();
            }

        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    // Get adress from coordinates
    private String getCompleteAddressString(double LATITUDE, double LONGITUDE) {
        String strAdd = "";
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(LATITUDE, LONGITUDE, 1);
            if (addresses != null) {
                Address returnedAddress = addresses.get(0);
                StringBuilder strReturnedAddress = new StringBuilder("");

                for (int i = 0; i <= returnedAddress.getMaxAddressLineIndex(); i++) {
                    strReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n");
                }
                strAdd = strReturnedAddress.toString();
                Log.w("Current loction address", strReturnedAddress.toString());
            } else {
                Log.w("Current loction address", "No Address returned!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.w("Current loction address", "Canont get Address!");
        }
        return strAdd;
    }

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this,  CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    /*@Override
    protected void onResume() {
        super.onResume();
        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }*/

   /* private void startLocationUpdates() {
        if (mFusedLocationClient != null)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
    }*/

    /*@Override
    protected void onPause() {
        super.onPause();
        if (mRequestingLocationUpdates) {
            stopLocationUpdates();
        }
    }*/
    /*private void stopLocationUpdates() {
        if (mFusedLocationClient != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }*/
}
