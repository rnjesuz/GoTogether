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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
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
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableLayout;
import expandablelib.gotogether.Section;

import static android.support.constraint.Constraints.TAG;

public class JoinEventOldActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    /** Id to identify a location permission request. */
    private static final int REQUEST_LOCATION = 0;
    /** Permissions required to use device location. */
    private static String[] PERMISSIONS_LOCATION = {Manifest.permission.ACCESS_FINE_LOCATION};
    /** Provider of the user geolocation */
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private GoogleApiClient googleApiClient;
    private GoogleMap mMap;
    private Marker mDestination;
    private String parents = "Do you volunteer as a Driver?";
    private String destination = null;
    private LatLng destinationLatLng = null;
    private String start = null;
    private boolean isDriver = false;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_event_old);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp); // change the return to parent button

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.destinationMap_join);
        mapFragment.getMapAsync(this);

        Bundle bundle = getIntent().getParcelableExtra("Destination");
        // Get destination
        destination = bundle.getString("destinationAddress");
        // Get destination latitude and longitude
        destinationLatLng = bundle.getParcelable("destinationLatLng");

        // Initialize both fragment (Start and Destination queries) for Google's Places API
        initializePlaceAutoCompleteFragments();
        // Initialize an ExpandableLayout that opens if user wants to volunteer as driver
        initializeExpandableLayout();
        // Set location service provider
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Build location request parameters
        buildLocationRequest();

        FloatingActionButton completeEvent = (FloatingActionButton) findViewById(R.id.join_done);
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

    private void buildLocationRequest() {
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
        // Build callback for new location request
        buildLocationCalback();
    }

    private void buildLocationCalback() {
        mLocationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                Location currentLocation = null;
                // TODO is this really only 1 location?
                for (Location location: locationResult.getLocations()) {
                    currentLocation = location;
                }

                // Get an address for the location and write it in the AutoComplete fragment
                start = getCompleteAddressString(currentLocation.getLatitude(), currentLocation.getLongitude());
                PlaceAutocompleteFragment startAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.start_autocomplete_fragment_join);
                startAutocompleteFragment.setText(start);

                // Remove continuous location updates after we get current location
                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            }};
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Move the camera
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(destinationLatLng, 12);
        mMap.moveCamera(cu);
        // Add some markers to the map, and add a data object to each marker.
        //Bitmap img = BitmapFactory.decodeResource(getResources(),R.drawable.destination_png); // new icon
        //BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(img);
        mDestination = mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination").snippet(destination));
        mDestination.showInfoWindow();
        // Set a listener for marker click.
        mMap.setOnMarkerClickListener(this);
    }

    /** Called when the user clicks a marker. */
    @Override
    public boolean onMarkerClick(final Marker marker) {

        // Show Toast with marker location
        Toast.makeText(this, marker.getTitle() + "\n" + marker.getSnippet(), Toast.LENGTH_SHORT).show();

        // Return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false;
    }

    private void initializeExpandableLayout() {
        // get the layout
        ExpandableLayout sectionLinearLayout = (ExpandableLayout) findViewById(R.id.el_join);
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

        // create wanted sections - one, to ask for driving possibility
        sectionLinearLayout.addSection(getSection());

        //create listeners for expansion or collapse of the layout
        sectionLinearLayout.setExpandListener((ExpandCollapseListener.ExpandListener<Driver>) (parentIndex, parent, view) -> {
            // layout expanded = volunteering for driving
            isDriver = true;
        });
        sectionLinearLayout.setCollapseListener((ExpandCollapseListener.CollapseListener<Driver>) (parentIndex, parent, view) -> {
            // layout collapsed = don't want to be driver
            isDriver = false;
            emptySeats = -1;
        });
    }

    private void initializePlaceAutoCompleteFragments() {

        // Set bounds to Portugal
        LatLngBounds portugalBounds = new LatLngBounds(new LatLng(32.2895, -31.4648), new LatLng(42.154311, -6.189159));

        //the fragment for the starting location
        PlaceAutocompleteFragment startAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.start_autocomplete_fragment_join);
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

        startAutocompleteFragment.setBoundsBias(portugalBounds);
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
     Validate activity fields
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void concludeCreation(View view) {
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

    public void createTextPopUpWindow(String message) {
        ConstraintLayout tConstraintLayout = (ConstraintLayout) findViewById(R.id.joinEventActivityCL);
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
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
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

    private void createConfirmationPopUpWindow() {
        ConstraintLayout cConstraintLayout = (ConstraintLayout) findViewById(R.id.joinEventActivityCL);
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

                    Parameters1
                        contentView : the popup's content
                        width : the popup's width
                        height : the popup's height
                */
        // Initialize a new instance of popup window
        cPopupWindow = new PopupWindow(
                customView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
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
            messageText.setText(Html.fromHtml(htmlMsg));
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
                JoinEventOldActivity.this.confirmEvent();

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
        /*Bundle args = new Bundle();
        LatLng destinationLatLng = getLocationFromAddress(getApplicationContext(), destination);
        args.putParcelable("destinationLatLng", destinationLatLng);*/
        Intent intent = new Intent(JoinEventOldActivity.this, OldEventActivity.class);
        intent.putExtra("Destination", (Bundle) getIntent().getParcelableExtra("Destination"));
        Bundle participantsBundle = getIntent().getParcelableExtra("Participants");
        participantsBundle.putParcelable("User", createUser());
        intent.putExtra("Participants", participantsBundle);
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

    public User createUser() {
        LatLng startLatLng = getLocationFromAddress(getApplicationContext(), start);
        User user;
        if (isDriver) {
            user = new User("teste", "Ricardo", start, startLatLng, emptySeats);
        } else {
            user = new User("teste", "Ricardo", start, startLatLng);
        }
        return user;
    }

    public void setMyLocation(View view) {
        Log.i(TAG, "My location button pressed. Checking permission.");
        // BEGIN_INCLUDE(location_permission)
        // Check if the Location permission is already available.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
         //&& ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                ){
            // Location permission has not been granted
            requestLocationPermission(view);
        } else {
            // Location permissions is already available, use last known location.
            Log.i(TAG, "LOCATION permission has already been granted.");
            // Check for GPS service
            if(checkGPS()) {
                if (checkConnectivity()) {
                    Log.i("Set My Location", "Getting new location");
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);

                /*Log.i(TAG, "GPS is turned on. Getting last known location");
                mFusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                            @SuppressLint("MissingPermission")
                            @Override
                            public void onSuccess(Location location) {
                                // Got last known location. In some rare situations this can be null.
                                if (location != null) {
                                    Log.i("Set My Location", "Using last known location");
                                    String locationAddress = getCompleteAddressString(location.getLatitude(), location.getLongitude());
                                    start = locationAddress;
                                    PlaceAutocompleteFragment startAutocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.start_autocomplete_fragment_join);
                                    startAutocompleteFragment.setText(start);
                                } else {
                                    Log.i("Set My Location", "Getting new location");
                                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null );
                                }
                            }
                        });*/

                }
                else {
                    Toast.makeText(JoinEventOldActivity.this,"No Internet connection detected.",Toast.LENGTH_SHORT).show();
                }
            }
        }

    }

    private boolean checkConnectivity(){
        ConnectivityManager cm =
                (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        return isConnected;
    }

    private boolean checkGPS() {
        boolean gpsEnabled = false;
        final LocationManager manager = (LocationManager) JoinEventOldActivity.this.getSystemService(Context.LOCATION_SERVICE);

        if(!hasGPSDevice(JoinEventOldActivity.this)){
            Log.e("GPS","Gps not supported");
            Toast.makeText(JoinEventOldActivity.this,"Gps not Supported",Toast.LENGTH_SHORT).show();
            gpsEnabled = false;
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(JoinEventOldActivity.this)) {
            Log.i("GPS","Gps not enabled");
            enableLoc();
            gpsEnabled = false;
        }else{
            Log.i("GPS","Gps is enabled");
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

            googleApiClient = new GoogleApiClient.Builder(JoinEventOldActivity.this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {

                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            googleApiClient.connect();
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {

                            Log.d("Location error","Location error " + connectionResult.getErrorCode());
                        }
                    }).build();
            googleApiClient.connect();

            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(5 * 1000);
            locationRequest.setFastestInterval(2 * 1000);
            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest);

            builder.setAlwaysShow(false);

            PendingResult<LocationSettingsResult> result =
                    LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
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
                                status.startResolutionForResult(JoinEventOldActivity.this, REQUEST_LOCATION);

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
            Log.i(TAG,"Displaying location permission rationale to provide additional context.");
            Snackbar.make(view, R.string.permission_location_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(JoinEventOldActivity.this, PERMISSIONS_LOCATION, REQUEST_LOCATION);
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
                Snackbar.make(findViewById(R.id.joinEventActivityCL), R.string.permission_available_location,
                        Snackbar.LENGTH_SHORT)
                        .show();
            } else {
                Log.i(TAG, "Location permissions were NOT granted.");
                Snackbar.make(findViewById(R.id.joinEventActivityCL), R.string.permissions_not_granted,
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
}
