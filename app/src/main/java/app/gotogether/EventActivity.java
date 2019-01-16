package app.gotogether;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.akexorcist.googledirection.DirectionCallback;
import com.akexorcist.googledirection.GoogleDirection;
import com.akexorcist.googledirection.constant.TransportMode;
import com.akexorcist.googledirection.model.Direction;
import com.akexorcist.googledirection.model.Leg;
import com.akexorcist.googledirection.model.Route;
import com.akexorcist.googledirection.model.Step;
import com.akexorcist.googledirection.util.DirectionConverter;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.functions.HttpsCallableResult;
import com.xw.repo.BubbleSeekBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import app.gotogether.PagerAdapter.TabItem;
import app.gotogether.fragments.ClusterListFragment;
import app.gotogether.fragments.ParticipantsListFragment;
import biz.laenger.android.vpbs.BottomSheetUtils;
import biz.laenger.android.vpbs.ViewPagerBottomSheetBehavior;

public class EventActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, ParticipantsListFragment.OnCompleteListener, ClusterListFragment.OnCompleteListener, ThreadCompleteListener {

    private static final String TAG = "EventActivity";
    private String destination = null;
    private LatLng destinationLatLng = null;
    private String start = null;
    private LatLng startLatLng = null;
    protected static GoogleMap mMap;
    private HashMap<String, Marker> markers = new HashMap<>();
    private User user;
    private ArrayList<User> participants;
    private static ViewPagerBottomSheetBehavior<View> bottomSheetBehavior;
    private LatLngBounds bounds;
    private String title;
    private String eventUID;
    private String owner;
    private Boolean complete = false;
    private Toolbar bottomSheetToolbar;
    private TabLayout bottomSheetTabLayout;
    private ViewPager bottomSheetViewPager;
    private PagerAdapter sectionsPagerAdapter;
    private ArrayList<String> myRouteCluster = new ArrayList<>();
    private FirebaseFunctions mFunctions;
    private ProgressBar progressBar;
    private SupportMapFragment mapFragment;
    private boolean concludingEvent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event);

        // initialize functions instance
        mFunctions = FirebaseFunctions.getInstance();

        // Get event's uid from intent
        eventUID = getIntent().getStringExtra("eventUID");
        // Get the event owner from intent
        owner = getIntent().getStringExtra("Owner");
        // Get title from Intent
        title = getIntent().getStringExtra("Title");
        getSupportActionBar().setTitle(title);
        // Get destination from Intent
        Bundle destinationBundle = getIntent().getParcelableExtra("Destination");
        destination = destinationBundle.getString("destinationAddress");
        destinationLatLng = destinationBundle.getParcelable("destinationLatLng");
        // Get participants from Intent
        Bundle participantsBundle = getIntent().getParcelableExtra("Participants");
        participants = participantsBundle.getParcelableArrayList("Participants");
        // Get user from Intent
        user = participantsBundle.getParcelable("User");
        // Get user pick-up location
        start = user.getStartAddress();
        startLatLng = user.getStartLatLng();
        // Is the event complete? - get from intent if yes
        Boolean possibleComplete = getIntent().getBooleanExtra("Completed", false);
        Log.i(TAG, "Event complete? "+possibleComplete);
        complete = possibleComplete;

        // Set up the map fragment
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_event);
        mapFragment.getMapAsync(this);

        setupBottomSheet();
    }

    private void setupBottomSheet() {
        LinearLayout bottomSheetFrame = findViewById(R.id.bottom_sheet);
        bottomSheetViewPager = findViewById(R.id.bottom_sheet_viewpager);
        bottomSheetTabLayout = findViewById(R.id.bottom_sheet_tabs);

        if (complete){
            Log.d(TAG, "complete");
            concludingEvent = true;
            sectionsPagerAdapter = new PagerAdapter(getSupportFragmentManager(), EventActivity.this, TabItem.PARTICIPANTS, TabItem.CLUSTER);
        }
        else {
            Log.d(TAG, "not complete");
            sectionsPagerAdapter = new PagerAdapter(getSupportFragmentManager(), EventActivity.this, TabItem.PARTICIPANTS);
        }
        bottomSheetViewPager.setOffscreenPageLimit(1);
        bottomSheetViewPager.setAdapter(sectionsPagerAdapter);
        bottomSheetTabLayout.setupWithViewPager(bottomSheetViewPager);
        BottomSheetUtils.setupViewPager(bottomSheetViewPager);
        // The View with the BottomSheetBehavior
        bottomSheetBehavior = ViewPagerBottomSheetBehavior.from(bottomSheetFrame);
        bottomSheetBehavior.setBottomSheetCallback(new ViewPagerBottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // React to state change
                Log.i(TAG, "onStateChanged:" + newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // React to dragging events
                Log.i(TAG, "onSlide");
            }
        });
        bottomSheetTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if(!concludingEvent) {
                    Log.d(TAG, "Tab Selected: expanding bottom sheet");
                    bottomSheetBehavior.setState(ViewPagerBottomSheetBehavior.STATE_EXPANDED);
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                int state = ViewPagerBottomSheetBehavior.from(bottomSheetFrame).getState();
                if (state == ViewPagerBottomSheetBehavior.STATE_EXPANDED) {
                    Log.d(TAG, "Tab RE-selected: collapsing bottom sheet");
                    bottomSheetBehavior.setState(ViewPagerBottomSheetBehavior.STATE_COLLAPSED);
                }
                else if (state == ViewPagerBottomSheetBehavior.STATE_COLLAPSED) {
                    Log.d(TAG, "Tab RE-selected: expanding bottom sheet");
                    bottomSheetBehavior.setState(ViewPagerBottomSheetBehavior.STATE_EXPANDED);
                }
            }
        });

        // sheet starts collapsed
        bottomSheetBehavior.setState(ViewPagerBottomSheetBehavior.STATE_COLLAPSED);

        // need a GlobalLayoutListener to wait for the view to be drawn
        final TabLayout tb = (TabLayout) findViewById(R.id.bottom_sheet_tabs);
        ViewTreeObserver vto = tb.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                // TabLayout was drawn now we can get it's height
                int tbHeight = tb.getHeight();
                // and set bottom sheet's peek height
                bottomSheetBehavior.setPeekHeight(tbHeight);

                // and set some padding to accommodate for bottom shelf - so the google logo shows
                mMap.setPadding(0, 0, 0, tbHeight);

                ViewTreeObserver obs = tb.getViewTreeObserver();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    obs.removeOnGlobalLayoutListener(this);
                } else {
                    obs.removeGlobalOnLayoutListener(this);
                }
            }

        });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void drawRoute() {
        List<LatLng> waypoints = new ArrayList<>();
        LatLng routeStartLatLng = null;
        for (int i=0; i<myRouteCluster.size(); i++){
            int counter = i;
            String uid = myRouteCluster.get(i);
            User u = participants.stream().filter(user -> uid.equals(user.getId())).findAny().orElse(null);
            if (u ==null){
                u =user;
            }
            if(i==0){
                routeStartLatLng = u.getStartLatLng();
            } else {
                waypoints.add(u.getStartLatLng());
            }
        }

        GoogleDirection.withServerKey(getResources().getString(R.string.google_api_key))
                .from(routeStartLatLng)
                .and(waypoints)
                .to(destinationLatLng)
                .transportMode(TransportMode.DRIVING)
                .optimizeWaypoints(true) // optimize the order of the waypoints
                .execute(new DirectionCallback() {
                    @Override
                    public void onDirectionSuccess(Direction direction, String rawBody) {
                        if(direction.isOK()) {
                            Route route = direction.getRouteList().get(0);
                            int legCount = route.getLegList().size();
                            for (int index = 0; index < legCount; index++) {
                                Leg leg = route.getLegList().get(index);
                                List<Step> stepList = leg.getStepList();
                                ArrayList<PolylineOptions> polylineOptionList = DirectionConverter.createTransitPolyline(EventActivity.this, stepList, 5, getResources().getColor(R.color.green_complementary), 3, Color.BLUE);
                                for (PolylineOptions polylineOption : polylineOptionList) {
                                    mMap.addPolyline(polylineOption);
                                }
                            }
                            setCameraWithCoordinationBounds(route);
                        }
                    }

                    @Override
                    public void onDirectionFailure(Throwable t) {
                        // Do something TODO
                    }
                });
    }

    private void setCameraWithCoordinationBounds(Route route) {
        LatLng southwest = route.getBound().getSouthwestCoordination().getCoordination();
        LatLng northeast = route.getBound().getNortheastCoordination().getCoordination();
        LatLngBounds bounds = new LatLngBounds(southwest, northeast);
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
    }

    public void onParticipantsListFragmentComplete() {
        ParticipantsListFragment fragment = (ParticipantsListFragment) sectionsPagerAdapter.getFragment(0);
        View inflatedView = fragment.getInflatedView();

        // the event part
        LinearLayout eventInfo = inflatedView.findViewById(R.id.event_info);
        eventInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goMarkerDestination();
            }
        });
        TextView eventTitle = inflatedView.findViewById(R.id.titleView);
        eventTitle.setText(new SpannableString(Html.fromHtml("<b>Title: </b>"+ title)));
        TextView eventDestination = inflatedView.findViewById(R.id.destinationView);
        eventDestination.setText(new SpannableString(Html.fromHtml("<b>Destination: </b>"+ destination)));
        // the user part
        LinearLayout userInfo = inflatedView.findViewById(R.id.user_info);
        userInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goMarkerUser();
            }
        });
        TextView userPickup = inflatedView.findViewById(R.id.pickupView);
        userPickup.setText(new SpannableString(Html.fromHtml("<b>Pickup: </b>"+ start)));
        TextView userDriver = inflatedView.findViewById(R.id.driverView);
        TextView userSeats = inflatedView.findViewById(R.id.seatsView);
        if(user.isDriver()){
            userDriver.setText(new SpannableString(Html.fromHtml("<b>Driver: </b>Available")));
            userSeats.setText(new SpannableString(Html.fromHtml("<b>Empty seats: </b>"+ user.getSeats())));
        } else {
            userDriver.setText(new SpannableString(Html.fromHtml("<b>Driver: </b>Not available")));
            userSeats.setVisibility(View.GONE);
        }
        // the participants part
        if(!participants.isEmpty()) {
            RecyclerView bottomSheet = inflatedView.findViewById(R.id.bottom_sheet_participants);
            // Create bottom sheet items
            ArrayList<User> items =  new ArrayList<>(participants);

            // Instantiate adapter
            UserInEventAdapter userDescriptionAdapter = new UserInEventAdapter(items, null);
            bottomSheet.setAdapter(userDescriptionAdapter);

            // Set the layout manager
            bottomSheet.setLayoutManager(new LinearLayoutManager(EventActivity.this));
        } else {
            Log.i(TAG, "no participants - removing participants Text view");
            TextView participantsView = inflatedView.findViewById(R.id.participantsView);
            RecyclerView participantsList = inflatedView.findViewById(R.id.bottom_sheet_participants);
            participantsView.setVisibility(View.GONE);
            participantsList.setVisibility(View.GONE);
        }
    }

    public void onClusterListFragmentComplete() {
        ClusterListFragment fragment = (ClusterListFragment) sectionsPagerAdapter.getFragment(1);
        View inflatedView = fragment.getInflatedView();
        // initialize the db
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Grab cluster from db
        DocumentReference eventRef = db.collection("events").document(eventUID);
        eventRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()){
                    DocumentSnapshot document = task.getResult();
                    Map<String, ArrayList<DocumentReference>> cluster = (HashMap<String, ArrayList<DocumentReference>>) document.get("cluster");
                    /*Query participantsRefs = db.collection("users").whereArrayContains("events", eventRef);
                    participantsRefs.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()){
                                // Get the username of all the participants
                                HashMap<String, String> participantsUID = new HashMap<>();
                                for (QueryDocumentSnapshot participant: task.getResult()){
                                    Log.d(TAG, "participant: "+participant);
                                    participantsUID.put(participant.getId(), participant.getString("username"));
                                }
                                // Compare who are the drivers and who are the riders
                                ArrayList<String> drivers = new ArrayList<>();
                                ArrayList<ArrayList<String>> riders = new ArrayList<>();
                                // Get all the drivers
                                for (String driverUID: cluster.keySet()){
                                    ArrayList<String> ridersUsername = new ArrayList<>();
                                    Boolean isUserRoute = false;

                                    // change the map marker relating to this driver
                                    changeDriverMarker(driverUID);

                                    ArrayList<DocumentReference> ridersRef = cluster.get(driverUID);
                                    drivers.add(participantsUID.get(driverUID));
                                    if (driverUID.equals(user.getId())){
                                        myRouteCluster.add(driverUID);
                                        isUserRoute = true;
                                    }
                                    // Get username of riders of said driver
                                    ArrayList<String> riderUID = new ArrayList<>();
                                    for (DocumentReference riderRef: ridersRef){
                                        String uid = riderRef.getId();
                                        riderUID.add(uid);
                                        ridersUsername.add(participantsUID.get(uid));
                                        if (isUserRoute){
                                            myRouteCluster.add(riderRef.getId());
                                        }
                                        if (riderRef.getId().equals(user.getId())){
                                            myRouteCluster.add(driverUID);
                                            myRouteCluster.addAll(riderUID);
                                            isUserRoute = true;
                                        }
                                    }
                                    riders.add(ridersUsername);
                                }
                                Log.d(TAG, "drivers: "+drivers);
                                Log.d(TAG, "riders: "+riders);
                                // Instantiate adapter
                                RecyclerView clustersList = inflatedView.findViewById(R.id.bottom_sheet_drivers);
                                DriverInEventAdapter driverDescriptionAdapter = new DriverInEventAdapter(drivers, riders, null, EventActivity.this);
                                clustersList.setAdapter(driverDescriptionAdapter);
                                // Set the layout manager
                                clustersList.setLayoutManager(new LinearLayoutManager(EventActivity.this));

                                // Draw the user Route on the map
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // needs api N
                                    drawRoute();
                                }
                                // make the cluster tab the selected one
                                bottomSheetViewPager.setCurrentItem(1);
                                // signal event concluded
                                concludingEvent = false;
                            } else {
                                Log.d(TAG, "Query failed with ", task.getException());
                            }
                        }
                    });*/
                    eventRef.collection("participants").get()
                            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                    if (task.isSuccessful()) {
                                        // Get the username of all the participants
                                        HashMap<String, String> participantsUID = new HashMap<>();
                                        for (DocumentSnapshot participant : task.getResult()) {Log.d(TAG, "participant: "+participant);
                                            participantsUID.put(participant.getId(), participant.getString("username"));
                                        }
                                        // Compare who are the drivers and who are the riders
                                        ArrayList<String> drivers = new ArrayList<>();
                                        ArrayList<ArrayList<String>> riders = new ArrayList<>();
                                        // Get all the drivers
                                        for (String driverUID: cluster.keySet()){
                                            ArrayList<String> ridersUsername = new ArrayList<>();
                                            Boolean isUserRoute = false;

                                            // change the map marker relating to this driver
                                            changeDriverMarker(driverUID);

                                            ArrayList<DocumentReference> ridersRef = cluster.get(driverUID);
                                            drivers.add(participantsUID.get(driverUID));
                                            if (driverUID.equals(user.getId())){
                                                myRouteCluster.add(driverUID);
                                                isUserRoute = true;
                                            }
                                            // Get username of riders of said driver
                                            ArrayList<String> riderUID = new ArrayList<>();
                                            for (DocumentReference riderRef: ridersRef){
                                                String uid = riderRef.getId();
                                                riderUID.add(uid);
                                                ridersUsername.add(participantsUID.get(uid));
                                                if (isUserRoute){
                                                    myRouteCluster.add(riderRef.getId());
                                                }
                                                if (riderRef.getId().equals(user.getId())){
                                                    myRouteCluster.add(driverUID);
                                                    myRouteCluster.addAll(riderUID);
                                                    isUserRoute = true;
                                                }
                                            }
                                            riders.add(ridersUsername);
                                        }
                                        Log.d(TAG, "drivers: "+drivers);
                                        Log.d(TAG, "riders: "+riders);
                                        // Instantiate adapter
                                        RecyclerView clustersList = inflatedView.findViewById(R.id.bottom_sheet_drivers);
                                        DriverInEventAdapter driverDescriptionAdapter = new DriverInEventAdapter(drivers, riders, null, EventActivity.this);
                                        clustersList.setAdapter(driverDescriptionAdapter);
                                        // Set the layout manager
                                        clustersList.setLayoutManager(new LinearLayoutManager(EventActivity.this));

                                        // Draw the user Route on the map
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // needs api N
                                            drawRoute();
                                        }
                                        // make the cluster tab the selected one
                                        bottomSheetViewPager.setCurrentItem(1);
                                        // signal event concluded
                                        concludingEvent = false;
                                    } else {
                                        Log.d(TAG, "Error getting subcollection.", task.getException());
                                    }
                                }
                            });
                } else {
                    Log.d(TAG, "Event get failed with ", task.getException());
                }
            }
        });
    }

    private void addTab(TabItem item) {
        runOnUiThread(() -> {
            sectionsPagerAdapter.addTabPage(item);
            bottomSheetTabLayout.setupWithViewPager(bottomSheetViewPager);
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        try {
            // Customise map styling via JSON file
            boolean success = mMap.setMapStyle( MapStyleOptions.loadRawResourceStyle( this, R.raw.maps_style_json));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }
        // create builder to center the map
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        // Add some markers to the map, and add a data object to each marker.
        // Participants markers
        if(participants != null) {
            for (User u : participants) {
                Marker m = mMap.addMarker(new MarkerOptions()
                        .position(u.getStartLatLng())
                        .title(u.getUsername())
                        .snippet(u.getStartAddress())
                        //.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_person_black_32dp))
                        // Lowest z-index to force marker to bee at bottom when overlapping
                        .zIndex(0));
                markers.put(u.getId(), m);
                boundsBuilder.include(u.getStartLatLng());
            }

        }
        // User pick-up location marker
        Marker userMarker = mMap.addMarker(new MarkerOptions()
                    .position(startLatLng)
                    .title("Pick-Up")
                    .snippet(start)
                    //.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .icon(bitmapDescriptorFromVector(this, R.drawable.ic_person_green_normal_32dp))
                    // Second highest z-index. Only lowest to destination marker
                    .zIndex(1));
        markers.put("Pick-Up", userMarker);
        boundsBuilder.include(startLatLng);
        // Destination marker
        Marker destinationMarker = mMap.addMarker(new MarkerOptions()
                .position(destinationLatLng)
                .title("Destination")
                .snippet(destination)
                .icon(bitmapDescriptorFromVector(this, R.drawable.ic_flag_green_complementary_48dp))
                // Highest z-index. Destination is the most important marker
                .zIndex(2));
        markers.put("Destination", destinationMarker);
        boundsBuilder.include(destinationLatLng);
        //mDestination.showInfoWindow();

        // get map bounds
        bounds = boundsBuilder.build();

        // Set a listener for marker click.
        mMap.setOnMarkerClickListener(this);
        googleMap.getUiSettings().setMapToolbarEnabled(false);

        // disable map tools
        googleMap.getUiSettings().setMapToolbarEnabled(false);

        // Center map between pick-up and destination
        final View mapView = getSupportFragmentManager().findFragmentById(R.id.map_event).getView(); //TODO remove? already a field in the activity
        if (mapView.getViewTreeObserver().isAlive()) {
            mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @SuppressLint("NewApi") // We check which build version we are using.
                @Override
                public void onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        mapView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
                }});
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        // Show Toast with marker location
        Toast.makeText(this,Html.fromHtml("<b>"+marker.getTitle() + ":</b><br />" + marker.getSnippet()), Toast.LENGTH_SHORT).show();

        // Return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false;
    }

    protected void changeDriverMarker(String markerIdentifier){
        Marker m;
        if (user.getId().equals(markerIdentifier)) {
            m = markers.get("Pick-Up");
            m.setIcon(bitmapDescriptorFromVector(this, R.drawable.ic_directions_car_green_normal_32dp));
        }
        else {
            m = markers.get(markerIdentifier);
            m.setIcon(bitmapDescriptorFromVector(this, R.drawable.ic_directions_car_black_32dp));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!complete){
            getMenuInflater().inflate(R.menu.event_menu, menu);
            /*menu.add(0, 1, 1, menuIconWithText(getResources().getDrawable(R.drawable.ic_done_all_white_24dp), getResources().getString(R.string.conclude_event)));
            menu.add(0, 2, 2, menuIconWithText(getResources().getDrawable(R.drawable.ic_person_add_white_24dp), getResources().getString(R.string.show_identifier)));
            menu.add(0, 3, 3, menuIconWithText(getResources().getDrawable(R.drawable.ic_edit), getResources().getString(R.string.edit_event)));*/

            // test for owner exclusive actions
            if(!user.getId().equals(owner)) {
                menu.getItem(1).setVisible(false);
                menu.getItem(2).setVisible(false);
            }

            android.support.v7.app.ActionBar bar = getSupportActionBar();
            bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#43a047")));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_conclude: {
                // send server trigger to conclude the event
                concludingEvent = true;
                showParametersPopUpWindow();
                break;
            }
            case R.id.action_show_identifier: {
                // show the identifier
                showEventIdentifier(eventUID);
                break;
            }
            case R.id.action_edit: {
                if(user.getId().equals(owner)) {
                    // edit the event's original info
                    Intent intent = new Intent(EventActivity.this, UpdateEventActivity.class);
                    intent.putExtra("eventUID", eventUID);
                    intent.putExtra("Owner", owner);
                    intent.putExtra("Title", title);
                    intent.putExtra("Start", start);
                    intent.putExtra("Destination", destination);
                    if (user.isDriver()) {
                        intent.putExtra("Driver", true);
                        intent.putExtra("Seats", user.getSeats());
                    } else
                        intent.putExtra("Driver", false);
                    intent.putExtra("Participants", getIntent().getBundleExtra("Participants"));
                    startActivity(intent);
                } else {
                    editUserInputs();
                }
            }
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Concludes the event
     * sends an http request to server
     */
    private void concludeEvent2() {
        HashMap<String, Object> data = new HashMap<>();
        data.put("eventUID", eventUID);

        mFunctions.getHttpsCallable("cluster_distance_route")
                .call(data)
                .continueWith(new Continuation<HttpsCallableResult, Void>() {
                    @Override
                    public Void then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                        // This continuation runs on either success or failure, but if the task
                        // has failed then getResult() will throw an Exception which will be
                        // propagated down.
                        Log.d("-----TEST-----", "BEGIN000");
                        //Object result =  task.getResult().getData();
                        Log.d("-----TEST-----", "SUCCESS");
                        //return (String) result;
                        return null;
                    }
                }).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (!task.isSuccessful()) { // not successful
                    Exception e = task.getException();
                    if (e instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException ffe = (FirebaseFunctionsException) e;
                        FirebaseFunctionsException.Code code = ffe.getCode();
                        Object details = ffe.getDetails();
                    }
                    Log.w(TAG, "calculateCluster:OnFailure", e);
                    Snackbar.make(findViewById(android.R.id.content), "An error occurred.", Snackbar.LENGTH_SHORT).show();
                    return;

                } else { // Successful
                    Snackbar.make(findViewById(android.R.id.content), "Success: ", Snackbar.LENGTH_SHORT).show();
                    //setupBottomSheet();
                }
                // String result = (String) task.getResult();
                Log.d("-----TEST-----", "SUCCESS1");
            }
        });
    }

    private void concludeEvent(int distanceParameter, int carsParameter){
        Thread thread = new NotifyingThread() {
            @Override
            public void doRun() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("https://europe-west1-graphic-theory-211215.cloudfunctions.net/cluster_route");
                    conn = (HttpURLConnection) url.openConnection();
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.connect();
                    String jsonParam = new JSONObject()
                            .put("eventUID", eventUID)
                            .put("distance", distanceParameter/100.0)
                            .put("cars", carsParameter/100.0)
                            .put("optimization", false)
                            .toString();
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(jsonParam);

                    os.flush();
                    os.close();

                    Log.i("STATUS", String.valueOf(conn.getResponseCode()));
                    Log.i("MSG", conn.getResponseMessage());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } finally {
                    conn.disconnect();
                }
            }
        };
        ((NotifyingThread) thread).addListener(this);
        thread.start();

        // Start progress bar for undetermined time
        mapFragment.getView().setVisibility(View.INVISIBLE);
        progressBar = findViewById(R.id.progress_circle);
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void notifyOfThreadComplete(Thread thread) {
        complete = true;
        invalidateOptionsMenu();
        addTab(TabItem.CLUSTER);
        // Stop progress bar
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                mapFragment.getView().setVisibility(View.VISIBLE);
            }
        });
    }

    /** Center map on the user marker
     * Initiated by a button in the bottom sheet */
    public void goMarkerUser(){
        bottomSheetBehavior.setState(ViewPagerBottomSheetBehavior.STATE_COLLAPSED); // collapse the sheet
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 17));
    }

    /** Center map on the destination marker
     * Initiated by a button in the bottom sheet */
    public void goMarkerDestination(){
        bottomSheetBehavior.setState(ViewPagerBottomSheetBehavior.STATE_COLLAPSED); // collapse the sheet
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 17));
    }

    /** Center map on a participant marker
     * Initiated by a button in the bottom sheet
     * @param participant the participant to center on */
    public static void goMarkerParticipant(User participant){
        bottomSheetBehavior.setState(ViewPagerBottomSheetBehavior.STATE_COLLAPSED); // collapse the sheet
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(participant.getStartLatLng(), 17));
    }

    /** Recenter the map */
    public void centerMap(View view) {
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
    }

    /** launches activity for user to edit his inputs for the event */
    public void editUserInputs(View view) {
        Intent intent = new Intent(EventActivity.this, JoinEventActivity.class);
        intent.putExtra("Owner", owner);
        intent.putExtra("eventUID", eventUID);
        intent.putExtra("Title", title);
        intent.putExtra("Destination", getIntent().getBundleExtra("Destination"));
        intent.putExtra("Participants", getIntent().getBundleExtra("Participants"));
        startActivity(intent);
    }

    /** launches activity for user to edit his inputs for the event */
    public void editUserInputs(){
        Intent intent = new Intent(EventActivity.this, JoinEventActivity.class);
        intent.putExtra("Owner", owner);
        intent.putExtra("eventUID", eventUID);
        intent.putExtra("Title", title);
        intent.putExtra("Start", start);
        intent.putExtra("Destination", getIntent().getBundleExtra("Destination"));
        intent.putExtra("Participants", getIntent().getBundleExtra("Participants"));
        startActivity(intent);
    }

    /** shows text alongside the icon */
    private CharSequence menuIconWithText(Drawable r, String title) {

        r.setBounds(0, 0, r.getIntrinsicWidth(), r.getIntrinsicHeight());
        SpannableString sb = new SpannableString("    " + title);
        ImageSpan imageSpan = new ImageSpan(r, ImageSpan.ALIGN_BOTTOM);
        sb.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return sb;
    }

    /**
     * Shows a PopUp window with the event's unique identifier
     * @param eventUID the event identifier to show
     *
     */
    public void showEventIdentifier(String eventUID) {
        CoordinatorLayout tConstraintLayout = (CoordinatorLayout) findViewById(R.id.eventLayout);
        PopupWindow tPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) EventActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.show_identifier_pop_up_window, null);

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
        TextView messageText = (TextView) customView.findViewById(R.id.id_tv);
        // Set text
        messageText.setText(eventUID);

        // Get a reference for the custom view copy button
        ImageButton copyButton = (ImageButton) customView.findViewById(R.id.ib_copy);
        // Set a click listener for the popup window close button
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Copy to Clipboard
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                //ClipData clip = ClipData.newPlainText("Identifier", "Enter this identifier in Go-Together to join my Event!: " + eventUID);
                ClipData clip = ClipData.newPlainText("Identifier", eventUID);
                clipboard.setPrimaryClip(clip);
                // Confirm copy to user bia Toast
                Toast.makeText(EventActivity.this, "Identifier copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

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

    /**
     * Shows a PopUp window with the sliders for clustering parameters
     */
    public void showParametersPopUpWindow() {
        CoordinatorLayout cConstraintLayout = (CoordinatorLayout) findViewById(R.id.eventLayout);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) EventActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.cluster_parameters_pop_up_window, null);

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
        String htmlTitle = "<b>What do you wish to minimize?</b>";
        Spanned s = Html.fromHtml(htmlTitle);
        SpannableString sString = new SpannableString(s);
        titleText.setText(sString);

        // Get a reference for the distance slider
        BubbleSeekBar distanceBar = (BubbleSeekBar) customView.findViewById(R.id.distanceBar);
        // Get a reference for the distance slider
        BubbleSeekBar carsBar = (BubbleSeekBar) customView.findViewById(R.id.carsBar);

        // Changing progress of either bar influences the other
        distanceBar.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
                if (fromUser){
                    int remainingProgress = 100 - progress;
                    carsBar.setProgress(remainingProgress);
                }
            }
            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat) { }
            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) { }
        });
        carsBar.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
                if (fromUser){
                    int remainingProgress = 100 - progress;
                    distanceBar.setProgress(remainingProgress);
                }
            }
            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat) { }
            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) { }
        });

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
                // conclude Event
                concludeEvent(distanceBar.getProgress(), carsBar.getProgress());
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

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}

interface ThreadCompleteListener {
    void notifyOfThreadComplete(final Thread thread);
}

abstract class NotifyingThread extends Thread {
    private final Set<ThreadCompleteListener> listeners
            = new CopyOnWriteArraySet<ThreadCompleteListener>();
    public final void addListener(final ThreadCompleteListener listener) {
        listeners.add(listener);
    }
    public final void removeListener(final ThreadCompleteListener listener) {
        listeners.remove(listener);
    }
    private final void notifyListeners() {
        for (ThreadCompleteListener listener : listeners) {
            listener.notifyOfThreadComplete(this);
        }
    }
    @Override
    public final void run() {
        try {
            doRun();
        } finally {
            notifyListeners();
        }
    }
    public abstract void doRun();
}