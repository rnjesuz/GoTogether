package app.gotogether;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class CreateEvent2Activity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "CreateActivity";
    private static final int UP_BUTTON_ID = 16908332; // because R.id.home doesn't seem to work....
    private String parents = "Do you volunteer as a Driver?";
    private boolean isDriver = true;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats
    int PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION = 1;
    int PLACE_AUTOCOMPLETE_REQUEST_CODE_START = 2;
    private String destination = null;
    private String start = null;
    private int searching = -1; // >0 means searching. 1 is for destinations; 2 is for pick-up
    private ActionBar actionBar;
    private EditText eventTitle;
    private EditText destinationET;
    private EditText startET;
    private GooglePlacesAutocompleteAdapter dataAdapter;
    private ListView placeSuggestions;
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
    // Map variables - Map and markers
    private GoogleMap mMap;
    private Marker mDestination;
    private Marker mStart;

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event2);

        // Get map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        // set the callback
        mapFragment.getMapAsync(this);

        // Set location service provider
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Build location request parameters
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds

        // set the adapter use to get place suggestions
        dataAdapter = new GooglePlacesAutocompleteAdapter(CreateEvent2Activity.this, R.layout.place_suggestion_view){};
        // get references to some layout views
        placeSuggestions =  findViewById(R.id.places_suggestions);
        destinationET = findViewById(R.id.destination_autocomplete);
        startET = findViewById(R.id.start_autocomplete);

        // Assign adapter to ListView
        placeSuggestions.setAdapter(dataAdapter);
        //enables filtering for the contents of the given ListView
        placeSuggestions.setTextFilterEnabled(true);
        // behavior when item is clicked
        placeSuggestions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int ClikedPosition, long id) {
                //Getting clicked item from list view
                switch (searching){
                    case 1: {
                        destination = dataAdapter.getItem(ClikedPosition);
                        destinationET.setText(destination);
                        addMapMarker("Destination", destination);
                        if (start == null) {
                            startET.requestFocus();
                            searching = 2;
                        } else {
                            destinationET.clearFocus();
                            hideKeyboard(CreateEvent2Activity.this);
                            CollapseAfterInput();
                            searching = -1;
                        }
                        break;
                    }

                    case 2: {
                        start = dataAdapter.getItem(ClikedPosition);
                        startET.setText(start);
                        addMapMarker("Start", start);
                        if (destination == null){
                            destinationET.requestFocus();
                            searching = 1;
                        }
                        else {
                            startET.clearFocus();
                            hideKeyboard(CreateEvent2Activity.this);
                            CollapseAfterInput();
                            searching = -1;
                        }
                        break;
                    }
                }
            }
        });

        // my toolbar is defined in the layout file
        Toolbar myToolbar = (Toolbar) findViewById(R.id.create_toolbar);
        setSupportActionBar(myToolbar);
        // Get a support ActionBar corresponding to this toolbar
        actionBar = getSupportActionBar();
        // Changing title
        //actionBar.setTitle(R.string.title_activity_create_event);

        // Set up your ActionBar
        actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        View view = getLayoutInflater().inflate(R.layout.title_actionbar_view,
                null);
        android.support.v7.app.ActionBar.LayoutParams layoutParams = new android.support.v7.app.ActionBar.LayoutParams(android.support.v7.app.ActionBar.LayoutParams.MATCH_PARENT,
                android.support.v7.app.ActionBar.LayoutParams.MATCH_PARENT);
        actionBar.setCustomView(view, (ActionBar.LayoutParams) layoutParams);
        Toolbar parent = (Toolbar) view.getParent();
        parent.setContentInsetsAbsolute(0, 0);
        // Enable the Up button
        actionBar.setDisplayHomeAsUpEnabled(true);
        // change the up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        // Grab EditText for title input from actionbar
        View actionBarView = actionBar.getCustomView();
        eventTitle = actionBarView.findViewById(R.id.titleField);
        // change title field end margin to mirror start margin
        ViewGroup.MarginLayoutParams titleParams = (ViewGroup.MarginLayoutParams) eventTitle.getLayoutParams();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics); // get pixels values for the display
        ViewTreeObserver observer= eventTitle.getViewTreeObserver(); // because the EditText isn't rendered right away, we set a listener
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                titleParams.rightMargin = displayMetrics.widthPixels - eventTitle.getWidth(); // change the margin to be the same on both sides - left side has th home button
                eventTitle.getViewTreeObserver().removeGlobalOnLayoutListener(this); // remove the listener to prevent repeated invocations
            }
        });
        // set slider as invisible
        LinearLayout suggestions = findViewById(R.id.suggestions_slider);
        suggestions.setVisibility(View.INVISIBLE);

        findViewById(R.id.destination_autocomplete).setBackgroundColor(getResources().getColor(R.color.white));

        destinationET.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // has drawable?  || 0 = left, 1 = top, 2 = right, 3 = bottom
                    if (destinationET.getCompoundDrawables()[2] != null) {
                        // clicked on the drawable?
                        if (event.getRawX() >= (destinationET.getRight() - destinationET.getLeft() - destinationET.getCompoundDrawables()[2].getBounds().width())) {
                            // delete destination addr
                            destination = null;
                            // remove corresponding marker
                            if (mDestination != null) {
                                mDestination.remove();
                                mDestination = null;
                            }
                            destinationET.setText(null);
                            destinationET.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); // remove the erase icon
                            // if text was cleared while slider is collapsed
                            if(searching < 1){
                                Log.i("teste", Integer.toString(searching));
                                destinationET.clearFocus();
                                return true;
                            }
                            return false;
                        }
                        // clicked outside drawable
                        else {
                            DestinationAutoComplete(v);
                            return false;
                        }
                    }
                    // no drawable
                    else {
                        DestinationAutoComplete(v);
                        return false;
                    }
                }
                return false;
            }
        });
        destinationET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // no text
                if(s.length() == 0){
                    // has drawable?  || 0 = left, 1 = top, 2 = right, 3 = bottom
                    if (destinationET.getCompoundDrawables()[2] != null)
                        destinationET.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
                } else {
                    destinationET.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_close_red_24dp,0);
                }
                dataAdapter.getFilter().filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        destinationET.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus){
                    dataAdapter.clear();
                    dataAdapter.getFilter().filter("");
                    dataAdapter.notifyDataSetChanged();
                }
            }
        });

        startET.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // has drawable?  || 0 = left, 1 = top, 2 = right, 3 = bottom
                    if (startET.getCompoundDrawables()[2] != null) {
                        // clicked on the drawable?
                        if (event.getRawX() >= (startET.getRight() - startET.getLeft() - startET.getCompoundDrawables()[2].getBounds().width())) {
                            start = null; // delete start addr
                            // remove corresponding marker
                            if(mStart!=null) {
                                mStart.remove();
                                mStart = null;
                            }
                            startET.setText(null);
                            startET.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                            // if text was cleared while slider is collapsed
                            if(searching < 1) {
                                Log.i("teste", Integer.toString(searching));
                                startET.clearFocus();
                                return true; // to prevent keyboard appearance
                            }
                            return false;
                        }
                        // clicked outside drawable
                        else {
                            StartAutoComplete(v);
                            return false;
                        }
                    }
                    // no drawable
                    else {
                        StartAutoComplete(v);
                        return false;
                    }
                }
                return false;
            }
        });
        startET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // no text
                if(s.length() == 0){
                    // has drawable?  || 0 = left, 1 = top, 2 = right, 3 = bottom
                    if (startET.getCompoundDrawables()[2] != null)
                        startET.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
                } else {
                    startET.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_close_red_24dp,0);
                }
                dataAdapter.getFilter().filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        startET.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus){
                    dataAdapter.clear();
                    dataAdapter.getFilter().filter("");
                    dataAdapter.notifyDataSetChanged();
                }
            }
        });

        Button myLocation = findViewById(R.id.my_location);
        // add a listener for click
        myLocation.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // get location for destination
                if (searching == 1){
                    setMyLocationDestination(v);
                    if (start == null) {
                        startET.requestFocus();
                        searching = 2;
                    } else {
                        hideKeyboard(CreateEvent2Activity.this);
                        CollapseAfterInput();
                        searching = -1;
                    }
                }
                // get location for pickup
                if (searching == 2){
                    setMyLocationStart(v);
                    if (destination == null) {
                        destinationET.requestFocus();
                        searching = 1;
                    } else {
                        hideKeyboard(CreateEvent2Activity.this);
                        CollapseAfterInput();
                        searching = -1;
                    }
                }
            }
        });
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
        // Set a listener for marker click.
        mMap.setOnMarkerClickListener(this);
        // disable map tools
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        // set some padding to restrict markers to bottom half
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        mMap.setPadding(0, screenHeight / 2, 0, (int)(20 * (getResources().getDisplayMetrics().densityDpi / 160)));
    }

    /** Called when the user clicks a marker. */
    @Override
    public boolean onMarkerClick(final Marker marker) {

        // Show Toast with marker location
        Toast.makeText(this, marker.getTitle() + "\n" + marker.getSnippet(), Toast.LENGTH_SHORT).show();
        // Show marker info window
        marker.showInfoWindow();

        // Return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return true;
    }

    private void addMapMarker(String label, String address){
        // Get LatLng
        LatLng markerLatLng = getLocationFromAddress(this, address);
        // Add some markers to the map, and add a data object to each marker.
        if (label.equals("Destination")){
            mDestination = mMap.addMarker(new MarkerOptions().position(markerLatLng).title(label).snippet(address));
            mDestination.showInfoWindow();
        }
        if (label.equals("Start")){
            mStart = mMap.addMarker(new MarkerOptions().position(markerLatLng).title(label).snippet(address).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            mStart.showInfoWindow();
        }
        // Move the camera
        moveCamera(markerLatLng);
    }

    /*private void removeMapMarker(Marker marker){
        marker.remove();
    }*/

    private void moveCamera(LatLng markerLatLng) {
        final LatLng[] mapCenter = {markerLatLng};
        // Test how many markers already on map
        if(mDestination==null || mStart==null){
            //at least one is null so we center on the new one
            CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(markerLatLng, 12);
            mMap.moveCamera(cu);
        }
        else {
            // we have both locations so we center on them
            // Center map between pick-up and destination
            final View mapView = getSupportFragmentManager().findFragmentById(R.id.map).getView();
            if (mapView.getViewTreeObserver().isAlive()) {
                mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @SuppressLint("NewApi") // We check which build version we are using.
                    @Override
                    public void onGlobalLayout() {
                        LatLngBounds bounds = new LatLngBounds.Builder()
                                .include(getLocationFromAddress(CreateEvent2Activity.this, destination))
                                .include(getLocationFromAddress(CreateEvent2Activity.this, start))
                                .build();

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        mapCenter[0] =  bounds.getCenter();
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
                    }});
            }
        }
    }

    /** Override method to change functionality in times where layout is CollapsedForInput() */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case UP_BUTTON_ID: // hardcoded cause i can't find the id name for the up button
                if(searching > 0) {
                    hideKeyboard(CreateEvent2Activity.this);
                    CollapseAfterInput();
                    searching = -1;
                }
                else
                    return false; // don't consume the action
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    /** Override method to change functionality in times where layout is CollapsedForInput() */
    public void onBackPressed(){
        if(searching > 0){
            hideKeyboard(CreateEvent2Activity.this);
            CollapseAfterInput();
            searching = -1;
        }
        else
            super.onBackPressed();
    }

    private void StartAutoComplete(View view) {
        // expand the Layout
        ExpandForInput(view);
        // indicate searching for pickup location
        searching = 2;
        // TODO handle the places predictions
    }

    public void DestinationAutoComplete(View view){
        //expand the layout
        ExpandForInput(view);
        // indicate searching for destination location
        searching = 1;
        // TODO handle place suggestions
    }

    private void ExpandForInput(View view) {
        // move the layout up by removing margins
        LinearLayout grandparent = findViewById(R.id.event_inputs);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) grandparent.getLayoutParams();
        params.leftMargin=0; params.rightMargin=0; params.topMargin=0;

        ImageView topImage= findViewById(R.id.square_drawable);
        ViewGroup.MarginLayoutParams paramsIMG = (ViewGroup.MarginLayoutParams) topImage.getLayoutParams();
        paramsIMG.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        topImage.setLayoutParams(paramsIMG);

        TextView topInsert = findViewById(R.id.destination_autocomplete);
        ViewGroup.MarginLayoutParams paramsTV = (ViewGroup.MarginLayoutParams) topInsert.getLayoutParams();
        paramsTV.topMargin = 0;
        topInsert.setLayoutParams(paramsTV);

        // add padding
        ConstraintLayout parent = findViewById(R.id.coordinates_inputs);
        parent.setPadding((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
                0,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()),
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        grandparent.requestLayout();
        // resize actionbar
        actionBar.setDisplayShowCustomEnabled(false);
        Toolbar mToolbar = findViewById(R.id.create_toolbar);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mToolbar.getLayoutParams();
        layoutParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
        mToolbar.setLayoutParams(layoutParams);
        // change action bar color
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.white_background_dark)));
        // change actionbar elevation
        actionBar.setElevation(0);
        // remove actionbar title
        actionBar.setTitle(null);
        // remove the driver layout
        ConstraintLayout driverInputs = findViewById(R.id.driver_inputs);
        driverInputs.setVisibility(View.GONE);
        // change up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_black_24dp); // the default arrow
        // hide FAB so it doesn't obstruct view
        findViewById(R.id.create_done).setVisibility(View.GONE);
        // slide up the suggestions layout
        LinearLayout suggestions = findViewById(R.id.suggestions_slider);
        suggestions.setVisibility(View.VISIBLE);
        suggestions.animate().translationY(0);
    }

    private void CollapseAfterInput() {
        // move the layout down by adding margins
        LinearLayout grandparent = findViewById(R.id.event_inputs);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) grandparent.getLayoutParams();
        params.leftMargin=(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        params.rightMargin=(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        params.topMargin=(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());

        ImageView topImage= findViewById(R.id.square_drawable);
        ViewGroup.MarginLayoutParams paramsIMG = (ViewGroup.MarginLayoutParams) topImage.getLayoutParams();
        paramsIMG.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        topImage.setLayoutParams(paramsIMG);

        TextView topInsert = findViewById(R.id.destination_autocomplete);
        ViewGroup.MarginLayoutParams paramsTV = (ViewGroup.MarginLayoutParams) topInsert.getLayoutParams();
        paramsTV.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        topInsert.setLayoutParams(paramsTV);

        // add padding
        ConstraintLayout parent = findViewById(R.id.coordinates_inputs);
        parent.setPadding(0,0,0,0);
        grandparent.requestLayout();
        grandparent.requestLayout();
        // resize actionbar
        actionBar.setDisplayShowCustomEnabled(true);
        Toolbar mToolbar = findViewById(R.id.create_toolbar);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mToolbar.getLayoutParams();
        layoutParams.height = android.app.ActionBar.LayoutParams.WRAP_CONTENT;
        mToolbar.setLayoutParams(layoutParams);
        // change action bar color
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.green_normal)));
        // change actionbar elevation
        actionBar.setElevation((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
        // add actionbar title
        actionBar.setTitle(R.string.title_activity_create_event);
        // set the driver layout visible
        ConstraintLayout driverInputs = findViewById(R.id.driver_inputs);
        driverInputs.setVisibility(View.VISIBLE);
        // change up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp); // the default arrow
        // redraw
        findViewById(R.id.create_done).setVisibility(View.VISIBLE);
        // slide down the suggestions layout
        LinearLayout suggestions = findViewById(R.id.suggestions_slider);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        suggestions.animate().translationY(screenHeight - parent.getHeight());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                destination = place.getAddress().toString();
                EditText destinationET = ((EditText) findViewById(R.id.destination_autocomplete));
                destinationET.setText(destination);
                destinationET.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_close_red_24dp,0);
                Log.i(TAG, "Place: " + place.getName());
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.i(TAG, status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }

        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE_START) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                start = place.getAddress().toString();
                ((TextView) findViewById(R.id.start_autocomplete)).setText(start);
                Log.i(TAG, "Place: " + place.getName());
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.i(TAG, status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
    }

    public void DriverVolunteer(View view){
        ConstraintLayout seatQuestion = (ConstraintLayout) findViewById(R.id.seat_question);
        TextView driverQuestion = (TextView) findViewById(R.id.driver_question_tv);
        seatQuestion.requestLayout();
        if(isDriver) {
            driverQuestion.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.checkbox_outlined, 0);
            slideDrawerUp(seatQuestion);
            isDriver = false;
        } else {
            driverQuestion.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.checkbox_fill, 0);
            slideDrawerDown(seatQuestion);
            isDriver = true;
        }
    }

    private void slideDrawerUp(ConstraintLayout view) {
        view.animate()
                .translationY(-view.getHeight()/2)
                .alpha(0.0f)
                .setDuration(1000)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        view.setVisibility(View.INVISIBLE);
                    }
                });
    }

    private void slideDrawerDown(ConstraintLayout view) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0.0f);

        int bottom = view.getTop();
        int top = view.getBottom();
        // Start the animation
        view.animate()
                .translationY(bottom-top+(view.getHeight()))
                .alpha(1.0f)
                .setDuration(1000)
                .setListener(null);
    }

    /**
     Finalize event creation. Validate activity fields.
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
        EditText seatsText = findViewById(R.id.seatNumberEdit);
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
        RelativeLayout cConstraintLayout = (RelativeLayout) findViewById(R.id.createEventLayout);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.confirmation_pop_up_window, null);

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
                CreateEvent2Activity.this.confirmEvent();

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
        RelativeLayout tConstraintLayout = (RelativeLayout) findViewById(R.id.createEventLayout);
        PopupWindow tPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.simple_text_pop_up_window, null);

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
        Intent intent = new Intent(CreateEvent2Activity.this, OldEventActivity.class);
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

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
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
            }
        }
    }

    private void requestLocationPermission(View view) {
        Log.i(TAG, "LOCATION permission has NOT been granted. Requesting permission.");

        /*// BEGIN_INCLUDE(location_permission_request)
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
                            ActivityCompat.requestPermissions(CreateEvent2Activity.this, PERMISSIONS_LOCATION, REQUEST_LOCATION);
                        }
                    })
                    .show();
        } else {*/
            // Location permission has not been granted yet. Request it directly.
            ActivityCompat.requestPermissions(this, PERMISSIONS_LOCATION, REQUEST_LOCATION);
        /*}*/
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
                Snackbar.make(findViewById(R.id.createEventLayout), R.string.permission_available_location,
                        Snackbar.LENGTH_SHORT)
                        .show();
            } else {
                Log.i(TAG, "Location permissions were NOT granted.");
                Snackbar.make(findViewById(R.id.createEventLayout), R.string.permissions_not_granted,
                        Snackbar.LENGTH_SHORT)
                        .show();
            }

        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean checkGPS() {
        boolean gpsEnabled = false;
        final LocationManager manager = (LocationManager) CreateEvent2Activity.this.getSystemService(Context.LOCATION_SERVICE);

        if (!hasGPSDevice(CreateEvent2Activity.this)) {
            Log.e("GPS", "Gps not supported");
            Toast.makeText(CreateEvent2Activity.this, "Gps not Supported", Toast.LENGTH_SHORT).show();
            gpsEnabled = false;
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(CreateEvent2Activity.this)) {
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
        mGoogleApiClient = new GoogleApiClient.Builder(CreateEvent2Activity.this)
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
                            status.startResolutionForResult(CreateEvent2Activity.this, REQUEST_LOCATION);

                            //finish();
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                }
            }
        });
    }

    private void buildLocationCallBack(String location){
        mLocationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                Location currentLocation = null;
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

    private void handleNewLocation(Location location, String splitter) {
        if (splitter.equals("start")) {
            String locationAddress = getCompleteAddressString(location.getLatitude(), location.getLongitude());
            start = locationAddress;
            startET.setText(start);
            addMapMarker("Start", start);
        }
        if (splitter.equals("destination")) {
            String locationAddress = getCompleteAddressString(location.getLatitude(), location.getLongitude());
            destination = locationAddress;
            destinationET.setText(destination);
            addMapMarker("Destination", destination);
        }
    }

    /** Get address from coordinates */
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
