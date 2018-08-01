package app.gotogether;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
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
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
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
import static app.gotogether.CreateEventActivity.hideKeyboard;

public class JoinEventActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

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
    private LatLng startLatLng;
    private String title = null;
    private boolean isDriver = true;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats
    private GooglePlacesAutocompleteAdapter dataAdapter;
    private ListView placeSuggestions;
    private EditText startET;
    private boolean searching;
    private boolean locationClick = false;
    private ActionBar actionBar;
    private Marker mStart;
    private LinearLayout suggestions;
    private static final int UP_BUTTON_ID = 16908332; // because R.id.home doesn't seem to work....

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_event);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close); // change the return to parent button

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Bundle bundle = getIntent().getParcelableExtra("Destination");
        // Get destination
        destination = bundle.getString("destinationAddress");
        // Get destination latitude and longitude
        destinationLatLng = bundle.getParcelable("destinationLatLng");

        // Get event title
        title = getIntent().getStringExtra("Title");

        // Set location service provider
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Build location request parameters
        buildLocationRequest();

        // get destination field - set it to destination addr
        TextView destinationTV = findViewById(R.id.destination_information);
        destinationTV.setText(destination);

        // set the adapter use to get place suggestions
        dataAdapter = new GooglePlacesAutocompleteAdapter(JoinEventActivity.this, R.layout.place_suggestion_view){};
        // get references to some layout views
        placeSuggestions =  findViewById(R.id.places_suggestions);
        startET = findViewById(R.id.start_autocomplete);

        // Assign adapter to ListView
        placeSuggestions.setAdapter(dataAdapter);
        //enables filtering for the contents of the given ListView
        placeSuggestions.setTextFilterEnabled(true);
        // behavior when item is clicked
        placeSuggestions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int ClickedPosition, long id) {
                //Getting clicked item from list view
                start = dataAdapter.getItem(ClickedPosition);
                startET.setText(start);
                addMapMarker("Start", start);
                startET.clearFocus();
                hideKeyboard(JoinEventActivity.this);
                CollapseAfterInput();
                searching = false;
            }
        });

        // Get a support ActionBar corresponding to this toolbar
        actionBar = getSupportActionBar();
        // Changing title
        actionBar.setTitle(title);

        // Enable the Up button
        actionBar.setDisplayHomeAsUpEnabled(true);
        // change the up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close);
        // set slider as invisible
        suggestions = findViewById(R.id.suggestions_slider);
        suggestions.setVisibility(View.INVISIBLE);

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
                            if(!searching) {
                                Log.i("teste", Boolean.toString(searching));
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
                setMyLocation(v);
                hideKeyboard(JoinEventActivity.this);
                CollapseAfterInput();
                searching = false;
            }
        });
    }

    private void buildLocationRequest() {
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
        // Build callback for new location request
        buildLocationCallback();
    }

    private void buildLocationCallback() {
        mLocationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.e("GPS", "LocationResult returned with null");
                    return;
                }

                Location currentLocation = null;
                // TODO is this really only 1 location?
                for (Location location: locationResult.getLocations()) {
                    currentLocation = location;
                }

                // Get an address for the location and write it in the AutoComplete EditText
                start = getCompleteAddressString(currentLocation.getLatitude(), currentLocation.getLongitude());
                locationClick = true;
                startET.setText(start);
                addMapMarker("Start", start);
                locationClick = false; // reset

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

        // Set a listener for marker click.
        mMap.setOnMarkerClickListener(this);
        // disable map tools
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        // set some padding to restrict markers to bottom half
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        mMap.setPadding(0, screenHeight / 2, 0, 0);
        // Add destination marker to the map
        addMapMarker("Destination", destination);
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
        // Add some markers to the map, and add a data object to each marker.
        if (label.equals("Destination")){
            mDestination = mMap.addMarker(new MarkerOptions().position(destinationLatLng).title(label).snippet(address));
            mDestination.showInfoWindow();
        }
        if (label.equals("Start")){
            startLatLng = getLocationFromAddress(this, address); // Get LatLng
            mStart = mMap.addMarker(new MarkerOptions().position(startLatLng).title(label).snippet(address).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            mStart.showInfoWindow();
        }
        // Move the camera
        moveCamera();
    }

    private void removeMapMarker(Marker marker){
        marker.remove();
    }

    private void moveCamera() {
        if(mStart==null){
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
                    }
                });
            }
        }
    }

    /** Override method to change functionality in times where layout is CollapsedForInput() */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case UP_BUTTON_ID: // hardcoded cause i can't find the id name for the up button
                if (searching) {
                    hideKeyboard(JoinEventActivity.this);
                    CollapseAfterInput();
                    searching = false;
                } else
                    return false; // don't consume the action
                return true;

            case R.id.action_create:
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
        if(searching){
            hideKeyboard(JoinEventActivity.this);
            CollapseAfterInput();
            searching = false;
        }
        else
            super.onBackPressed();
    }

    private void StartAutoComplete() {
        // expand the Layout
        ExpandForInput();
        // indicate searching for pickup location
        searching = true;
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

        TextView topInsert = findViewById(R.id.destination_information);
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
        // remove actionbar title
        actionBar.setTitle("");
        // remove the driver layout
        ConstraintLayout driverInputs = findViewById(R.id.driver_inputs);
        driverInputs.setVisibility(View.GONE);
        // change up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_black); // the default arrow
        // change create option color
        TextView create = findViewById(R.id.action_create);
        create.setTextColor(getResources().getColor(R.color.black));
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

        TextView topInsert = findViewById(R.id.destination_information);
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
        // remove actionbar title
        actionBar.setTitle(title);
        // set the driver layout visible
        ConstraintLayout driverInputs = findViewById(R.id.driver_inputs);
        driverInputs.setVisibility(View.VISIBLE);
        // change up button icon
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close); // the default arrow
        // change create option color
        TextView create = findViewById(R.id.action_create);
        create.setTextColor(getResources().getColor(R.color.white));
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
        return super.onCreateOptionsMenu(menu);
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
     Finalize event creation.
     Validate activity fields
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void concludeCreation() {
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

    public void createTextPopUpWindow(String message) {
        RelativeLayout tConstraintLayout = (RelativeLayout) findViewById(R.id.joinEventLayout);
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
        RelativeLayout cConstraintLayout = (RelativeLayout) findViewById(R.id.joinEventLayout);
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
                JoinEventActivity.this.confirmEvent();

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
        Intent intent = new Intent(JoinEventActivity.this, EventActivity.class);
        // Title
        intent.putExtra("Title", title);
        // Destination
        intent.putExtra("Destination", (Bundle) getIntent().getParcelableExtra("Destination"));
        // PickUp
        Bundle participantsBundle = getIntent().getParcelableExtra("Participants");
        participantsBundle.putParcelable("User", createUser());
        intent.putExtra("Participants", participantsBundle);
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
            user = new User("Ricardo", start, startLatLng, emptySeats);
        } else {
            user = new User("Ricardo", start, startLatLng);
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
                }
                else {
                    Toast.makeText(JoinEventActivity.this,"No Internet connection detected.",Toast.LENGTH_SHORT).show();
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
        final LocationManager manager = (LocationManager) JoinEventActivity.this.getSystemService(Context.LOCATION_SERVICE);

        if(!hasGPSDevice(JoinEventActivity.this)){
            Log.e("GPS","Gps not supported");
            Toast.makeText(JoinEventActivity.this,"Gps not Supported",Toast.LENGTH_SHORT).show();
            gpsEnabled = false;
        }

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(JoinEventActivity.this)) {
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

            googleApiClient = new GoogleApiClient.Builder(JoinEventActivity.this)
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
                                status.startResolutionForResult(JoinEventActivity.this, REQUEST_LOCATION);

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
                            ActivityCompat.requestPermissions(JoinEventActivity.this, PERMISSIONS_LOCATION, REQUEST_LOCATION);
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
