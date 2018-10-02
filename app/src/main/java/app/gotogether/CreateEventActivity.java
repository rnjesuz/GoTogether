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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
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

public class CreateEventActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "CreateActivity";
    private static final int UP_BUTTON_ID = 16908332; // because R.id.home doesn't seem to work....
    private String parents = "Do you volunteer as a Driver?";
    private boolean isDriver = true;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats
    int PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION = 1;
    int PLACE_AUTOCOMPLETE_REQUEST_CODE_START = 2;
    private LatLng destinationLatLng = null;
    private LatLng startLatLng = null;
    private static String destination = null;
    private static String start = null;
    private String title = "";
    private int searching = -1; // >0 means searching. 1 is for destinations; 2 is for pick-up
    private boolean locationClick = false; // is location input being set through button?
    private ActionBar actionBar;
    private EditText eventTitle;
    private EditText destinationET;
    private EditText startET;
    private EditText titleET;
    private LinearLayout suggestions;
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
        setContentView(R.layout.activity_create_event);

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
        dataAdapter = new GooglePlacesAutocompleteAdapter(CreateEventActivity.this, R.layout.place_suggestion_view){};
        // get references to some layout views
        placeSuggestions =  findViewById(R.id.places_suggestions);
        destinationET = findViewById(R.id.destination_autocomplete);
        startET = findViewById(R.id.start_autocomplete);
        titleET = findViewById(R.id.title_input);

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
                        // reseting info
                        if(mDestination!=null) {
                            mDestination.remove();
                            mDestination = null;
                        }
                        destination = dataAdapter.getItem(ClikedPosition);
                        invalidateOptionsMenu();
                        destinationET.setText(destination);
                        addMapMarker("Destination", destination);
                        if (start == null) {
                            startET.requestFocus();
                            searching = 2;
                        } else {
                            destinationET.clearFocus();
                            hideKeyboard(CreateEventActivity.this);
                            CollapseAfterInput();
                            searching = -1;
                        }
                        break;
                    }

                    case 2: {
                        // reseting info
                        if(mStart!=null) {
                            mStart.remove();
                            mStart = null;
                        }
                        start = dataAdapter.getItem(ClikedPosition);
                        invalidateOptionsMenu();
                        startET.setText(start);
                        addMapMarker("Start", start);
                        if (destination == null){
                            destinationET.requestFocus();
                            searching = 1;
                        }
                        else {
                            startET.clearFocus();
                            hideKeyboard(CreateEventActivity.this);
                            CollapseAfterInput();
                            searching = -1;
                        }
                        break;
                    }
                }
            }
        });

        // Get a support ActionBar corresponding to this toolbar
        actionBar = getSupportActionBar();
        // Changing title
        //actionBar.setTitle(getResources().getString(R.string.title_activity_create_event));
        actionBar.setTitle("");

        // Enable the Up button
        actionBar.setDisplayHomeAsUpEnabled(true);
        // change the up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close);

        // set slider as invisible
        // TODO
        suggestions = findViewById(R.id.suggestions_slider);
        if (suggestions.getViewTreeObserver().isAlive()) {
            suggestions.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @SuppressLint("NewApi") // We check which build version we are using.
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        suggestions.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        suggestions.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                    suggestions.setVisibility(View.INVISIBLE);
                }});
        }

        //findViewById(R.id.destination_autocomplete).setBackgroundColor(getResources().getColor(R.color.white));

        /*titleET.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            /**
             * Called when an action is being performed.
             *
             * @param v        The view that was clicked.
             * @param actionId Identifier of the action.  This will be either the
             *                 identifier you supplied, or {@link EditorInfo#IME_NULL
             *                 EditorInfo.IME_NULL} if being called due to the enter key
             *                 being pressed.
             * @param event    If triggered by an enter key, this is the event;
             *                 otherwise, this is null.
             * @return Return true if you have consumed the action, else false.
             */
            /*
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    title = v.getText().toString();
                    hideKeyboard(CreateEventActivity.this);
                    v.clearFocus();
                    return true;
                }
                return false;
            }
        });*/
        titleET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                title = s.toString();
                invalidateOptionsMenu();
            }
        });

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
                            invalidateOptionsMenu();
                            // remove corresponding marker
                            if (mDestination != null) {
                                mDestination.remove();
                                mDestination = null;
                            }
                            destinationET.setText(null);
                            destinationET.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); // remove the erase icon
                            // if text was cleared while slider is collapsed
                            if(searching < 1){
                                destinationET.clearFocus();
                                return true;
                            }
                            return false;
                        }
                        // clicked outside drawable
                        else {
                            DestinationAutoComplete();
                            return false;
                        }
                    }
                    // no drawable
                    else {
                        DestinationAutoComplete();
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

                    // delete start info
                    CreateEventActivity.destination = null;
                    invalidateOptionsMenu();
                    destinationLatLng = null;
                    if(mDestination!=null) {
                        mDestination.remove();
                        mDestination = null;
                    }
                } else {
                    destinationET.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_close_red,0);
                }
                if (!locationClick)
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
                            invalidateOptionsMenu();
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
                            StartAutoComplete();
                            return false;
                        }
                    }
                    // no drawable
                    else {
                        StartAutoComplete();
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

                    // delete start info
                    CreateEventActivity.start = null;
                    invalidateOptionsMenu();
                    startLatLng = null;
                    if(mStart!=null) {
                        mStart.remove();
                        mStart = null;
                    }
                } else {
                    startET.setCompoundDrawablesWithIntrinsicBounds(0,0, R.drawable.ic_close_red,0);
                }
                if (!locationClick)
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
                        hideKeyboard(CreateEventActivity.this);
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
                        hideKeyboard(CreateEventActivity.this);
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
        mMap.setPadding(0, screenHeight / 2, 0, 0);
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
            destinationLatLng = markerLatLng;

        }
        if (label.equals("Start")){
            mStart = mMap.addMarker(new MarkerOptions().position(markerLatLng).title(label).snippet(address).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            mStart.showInfoWindow();
            startLatLng = markerLatLng;
        }
        // Move the camera
        moveCamera();
    }

    private void removeMapMarker(Marker marker){
        marker.remove();
    }

    private void moveCamera() {
        // Test how many markers already on map
        if(mDestination==null){
            //no pickup so we center on destination
            CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(startLatLng, 12);
            mMap.moveCamera(cu);
        }
        else if(mStart==null){
            //no destination so we center on pickup
            CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(destinationLatLng, 12);
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
                                .include(destinationLatLng)
                                .include(startLatLng)
                                .build();

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
                    }});
            }
        }
    }

    /** Override method to change functionality in times where layout is CollapsedForInput() */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case UP_BUTTON_ID: // hardcoded cause i can't find the id name for the up button
                if (searching > 0) {
                    hideKeyboard(CreateEventActivity.this);
                    CollapseAfterInput();
                    searching = -1;
                } else
                    return false; // don't consume the action
                return true;

            case R.id.action_create:
                hideKeyboard(CreateEventActivity.this);
                concludeCreation();
                return false;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    /** Override method to change functionality in times where layout is CollapsedForInput() */
    public void onBackPressed(){
        if(searching > 0){
            hideKeyboard(CreateEventActivity.this);
            CollapseAfterInput();
            searching = -1;
        }
        else
            super.onBackPressed();
    }

    private void StartAutoComplete() {
        // expand the Layout
        ExpandForInput();
        // indicate searching for pickup location
        searching = 2;
        // TODO handle the places predictions
    }

    public void DestinationAutoComplete(){
        //expand the layout
        ExpandForInput();
        // indicate searching for destination location
        searching = 1;
        // TODO handle place suggestions
    }

    private void ExpandForInput() {
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
        // change action bar color
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.white_background_dark)));
        // change actionbar elevation
         //TODO or maybe set the input fields elevation?
        actionBar.setElevation(0);
        //remove the title input layout
        TextInputLayout titleField = findViewById(R.id.create_title);
        titleField.setVisibility(View.GONE);
        // remove the driver layout
        ConstraintLayout driverInputs = findViewById(R.id.driver_inputs);
        driverInputs.setVisibility(View.GONE);
        // change up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_black); // the default arrow
        // change create option color
        TextView create = findViewById(R.id.action_create);
        create.setVisibility(View.GONE);
        // slide up the suggestions layout
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
        // change action bar color
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.green_normal)));
        // change actionbar elevation
        actionBar.setElevation((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
        // set the title input layout visible
        TextInputLayout titleField = findViewById(R.id.create_title);
        titleField.setVisibility(View.VISIBLE);
        // set the driver layout visible
        ConstraintLayout driverInputs = findViewById(R.id.driver_inputs);
        driverInputs.setVisibility(View.VISIBLE);
        // change up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close); // the default arrow
        // change create option color
        TextView create = findViewById(R.id.action_create);
        create.setVisibility(View.VISIBLE);
        // slide down the suggestions layout
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        suggestions.animate().translationY(screenHeight - parent.getHeight());
    }

    // create an action bar button
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.create_menu, menu);
        menu.getItem(0).setEnabled(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {

        if (start != null && destination != null && !title.equals("")) {
            menu.getItem(0).setEnabled(true);
        }
        else
            menu.getItem(0).setEnabled(false);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                destination = place.getAddress().toString();
                invalidateOptionsMenu();
                EditText destinationET = ((EditText) findViewById(R.id.destination_autocomplete));
                destinationET.setText(destination);
                destinationET.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_close_red,0);
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
                invalidateOptionsMenu();
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
    public void concludeCreation() {
        // Has title?
        if (title.equals("")) {
            // Send error message
            createTextPopUpWindow("The event needs a title.");
            return;
        }
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

        // Get a reference for the custom view title
        TextView titleText = (TextView) customView.findViewById(R.id.titleTV);
        String htmlTitle = "<b>"+title+"</b>";
        Spanned s = Html.fromHtml(htmlTitle);
        SpannableString sString = new SpannableString(s);
        titleText.setText(sString);

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
                CreateEventActivity.this.confirmEvent();

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
        Intent intent = new Intent(CreateEventActivity.this, EventActivity.class);
        // Title
        intent.putExtra("Title", title);
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
            user = new User("Ricardo", start, startLatLng, emptySeats);
        } else {
            user = new User("Ricardo", start, startLatLng);
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
        ActivityCompat.requestPermissions(this, PERMISSIONS_LOCATION, REQUEST_LOCATION);
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
        final LocationManager manager = (LocationManager) CreateEventActivity.this.getSystemService(Context.LOCATION_SERVICE);

        if (!hasGPSDevice(CreateEventActivity.this)) {
            Log.e("GPS", "Gps not supported");
            Toast.makeText(CreateEventActivity.this, "Gps not Supported", Toast.LENGTH_SHORT).show();
            gpsEnabled = false;
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(CreateEventActivity.this)) {
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
        mGoogleApiClient = new GoogleApiClient.Builder(CreateEventActivity.this)
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
                            status.startResolutionForResult(CreateEventActivity.this, REQUEST_LOCATION);

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
        locationClick = true; // setting location via button - prevents AP*I calling from the Places Adapter
        if (splitter.equals("start")) {
            String locationAddress = getCompleteAddressString(location.getLatitude(), location.getLongitude());
            start = locationAddress;
            invalidateOptionsMenu();
            startET.setText(start);
            addMapMarker("Start", start);
        }
        if (splitter.equals("destination")) {
            String locationAddress = getCompleteAddressString(location.getLatitude(), location.getLongitude());
            destination = locationAddress;
            invalidateOptionsMenu();
            destinationET.setText(destination);
            addMapMarker("Destination", destination);
        }
        locationClick = false; // reset
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
