package app.gotogether;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;

import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableParticipantLayout;
import expandablelib.gotogether.Section;

public class EventActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private String destination = null;
    private LatLng destinationLatLng = null;
    private String start = null;
    private LatLng startLatLng = null;
    protected static GoogleMap mMap;
    private User user;
    private ArrayList<User> participants;
    private static BottomSheetBehavior bottomSheetBehavior;
    private LatLngBounds bounds;
    private String title;
    private String eventUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event);

        // Get event's uid from intent
        eventUID = getIntent().getStringExtra("eventUID");
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
        //Get user pick-up location
        start = user.getStartAddress();
        startLatLng = user.getStartLatLng();

        // Set up the map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_event);
        mapFragment.getMapAsync(this);

        CoordinatorLayout eevntLayout = (CoordinatorLayout) findViewById(R.id.eventLayout);
        LinearLayout bottomSheetFrame = (LinearLayout) findViewById(R.id.bottom_sheet_frame);
        ImageView bottomSheetImage = (ImageView) findViewById(R.id.bottom_sheet_image);
        // the event part
        TextView eventTitle = findViewById(R.id.titleView);
        eventTitle.setText(new SpannableString(Html.fromHtml("<b>Title: </b>"+ title)));
        TextView eventDestination = findViewById(R.id.destinationView);
        eventDestination.setText(new SpannableString(Html.fromHtml("<b>Destination: </b>"+ destination)));
        // the user part
        TextView userPIckup = findViewById(R.id.pickupView);
        userPIckup.setText(new SpannableString(Html.fromHtml("<b>Pickup: </b>"+ start)));
        TextView userDriver = findViewById(R.id.driverView);
        TextView userSeats = findViewById(R.id.seatsView);
        if(user.isDriver()){
            userDriver.setText(new SpannableString(Html.fromHtml("<b>Driver: </b>Available")));
            userSeats.setText(new SpannableString(Html.fromHtml("<b>Empty seats: </b>"+ user.getSeats())));
        } else {
            userDriver.setText(new SpannableString(Html.fromHtml("<b>Driver: </b>Not available")));
            userSeats.setVisibility(View.GONE);
        }
        // the participants part
        RecyclerView bottomSheet = findViewById(R.id.bottom_sheet_participants);
        // Create bottom sheet items
        ArrayList<User> items = null;
        if(participants != null) {
            items = new ArrayList<>(participants);
        }

        // Instantiate adapter
        UserInEventAdapter userDescriptionAdapter = new UserInEventAdapter(items, null);
        bottomSheet.setAdapter(userDescriptionAdapter);

        // Set the layout manager
        bottomSheet.setLayoutManager(new LinearLayoutManager(this));

        // The View with the BottomSheetBehavior
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetFrame);
        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // React to state change
                Log.i("onStateChanged", "onStateChanged:" + newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // React to dragging events
                Log.i("onSlide", "onSlide");
            }
        });

        bottomSheetBehavior.setPeekHeight(100);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // create builder to center the map
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        // Add some markers to the map, and add a data object to each marker.
        // Participants markers
        if(participants != null) {
            for (User u : participants) {
                mMap.addMarker(new MarkerOptions()
                        .position(u.getStartLatLng())
                        .title(u.getUsername())
                        .snippet(u.getStartAddress())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                        // Lowest z-index to force marker to bee at bottom when overlapping
                        .zIndex(0));
                boundsBuilder.include(u.getStartLatLng());
            }

        }
        // User pick-up location marker
        mMap.addMarker(new MarkerOptions()
                    .position(startLatLng)
                    .title("Pick-Up")
                    .snippet(start)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    // Second highest z-index. Only lowest to destination marker
                    .zIndex(1));
        boundsBuilder.include(startLatLng);
        // Destination marker
        mMap.addMarker(new MarkerOptions()
                .position(destinationLatLng)
                .title("Destination")
                .snippet(destination)
                // Highest z-index. Destination is the most important marker
                .zIndex(2));
        boundsBuilder.include(destinationLatLng);
        //mDestination.showInfoWindow();

        // get map bounds
        bounds = boundsBuilder.build();

        // Set a listener for marker click.
        mMap.setOnMarkerClickListener(this);
        googleMap.getUiSettings().setMapToolbarEnabled(false);

        // disable map tools
        googleMap.getUiSettings().setMapToolbarEnabled(false);

        // set some padding to accommodate for bottom shelf
        /*DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        mMap.setPadding(0, screenHeight / 2, 0, 0);*/

        // Center map between pick-up and destination
        final View mapView = getSupportFragmentManager().findFragmentById(R.id.map_event).getView();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.event_menu, menu);
        /*menu.add(0, 1, 1, menuIconWithText(getResources().getDrawable(R.drawable.ic_done_all_white_24dp), getResources().getString(R.string.conclude_event)));
        menu.add(0, 2, 2, menuIconWithText(getResources().getDrawable(R.drawable.ic_person_add_white_24dp), getResources().getString(R.string.show_identifier)));
        menu.add(0, 3, 3, menuIconWithText(getResources().getDrawable(R.drawable.ic_edit), getResources().getString(R.string.edit_event)));*/

        android.support.v7.app.ActionBar bar = getSupportActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#43a047")));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_conclude: {
                concludeEvent();
                break;
            }
            case R.id.action_show_identifier: {
                // show the identifier
                showEventIdentifier(eventUID);
                break;
            }
            case R.id.action_edit: {
                // edit the event's original info
                Intent intent = new Intent(EventActivity.this, UpdateEventActivity.class);
                intent.putExtra("Title", title);
                intent.putExtra("Start", start);
                intent.putExtra("Destination", destination);
                if(user.isDriver()){
                    intent.putExtra("Driver", true);
                    intent.putExtra("Seats", user.getSeats());
                } else
                    intent.putExtra("Driver", false);
                startActivity(intent);
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
     * updates the database as a completed event
     * sends an http request to calculate clusters
     * TODO redraw the event? launch new activity? Update the adapter?
     */
    private void concludeEvent() {
    }

    /** Center map on the user marker
     * Initiated by a button in the bottom sheet */
    public void goMarkerUser(View view){
        bottomSheetBehavior.setState(4); // collapse the sheet
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 15));
    }

    /** Center map on the destination marker
     * Initiated by a button in the bottom sheet */
    public void goMarkerDestination(View view){
        bottomSheetBehavior.setState(4); // collapse the sheet
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15));
    }

    /** Center map on a participant marker
     * Initiated by a button in the bottom sheet
     * @param participant the participant we each to center on */
    public static void goMarkerParticipant(User participant){
        bottomSheetBehavior.setState(4); // collapse the sheet
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(participant.getStartLatLng(), 15));
    }

    /** Recenter the map */
    public void centerMap(View view) {
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
    }


    /** launches activity for user to edit his inputs for the event */
    public void editUserInputs(View view) {
        Intent intent = new Intent(EventActivity.this, JoinEventActivity.class);
        intent.putExtra("eventUID", eventUID);
        intent.putExtra("Title", title);
        intent.putExtra("Destination", getIntent().getBundleExtra("Destination"));
        intent.putExtra("Participants", getIntent().getBundleExtra("Participants"));
        startActivity(intent);
    }

    // shows text alongside the icon
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
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

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
                ClipData clip = ClipData.newPlainText("Identifier", "Enter this identifier in Go-Together to join my Event!: " + eventUID);
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
}
