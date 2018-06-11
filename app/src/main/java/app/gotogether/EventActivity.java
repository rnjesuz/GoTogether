package app.gotogether;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.ArrayList;

import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableParticipantLayout;
import expandablelib.gotogether.Section;

public class EventActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private String destination = null;
    private LatLng destinationLatLng = null;
    private String start = null;
    private LatLng startLatLng = null;
    private String[] participantsAddresses = null;
    private ArrayList<LatLng> participantsLatLng = null;
    private GoogleMap mMap;
    private Marker mDestination;
    private Marker mStart;
    private String[] parents;
    private boolean isDriver = false;
    private int seats;
    private User user;
    private ArrayList<User> participants;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event);

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

        // Initialize an ExpandableLayout that opens if user wants to volunteer as driver
        initializeExpandableLayout();

        // Set up the map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_event);
        mapFragment.getMapAsync(this);


        final View mapView = getSupportFragmentManager().findFragmentById(R.id.map_event).getView();
        if (mapView.getViewTreeObserver().isAlive()) {
            mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @SuppressLint("NewApi") // We check which build version we are using.
                @Override
                public void onGlobalLayout() {
                    LatLngBounds bounds = new LatLngBounds.Builder()
                            .include(user.getStartLatLng())
                            .include(destinationLatLng)
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

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Move the camera
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 12));
        // Add some markers to the map, and add a data object to each marker.
        // Destination marker
        mDestination = mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination").snippet(destination));
        //mDestination.showInfoWindow();
        // User pick-up location marker
        mStart = mMap.addMarker(new MarkerOptions().position(startLatLng).title("Pick-Up").snippet(start).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        // Participants markers
        if(participants != null) {
            for (User u : participants) {
                mMap.addMarker(new MarkerOptions()
                        .position(u.getStartLatLng())
                        .title(u.getUsername())
                        .snippet(u.getStartAddress())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            }

        }

        // Set a listener for marker click.
        mMap.setOnMarkerClickListener(this);
        googleMap.getUiSettings().setMapToolbarEnabled(false);
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

    private void initializeExpandableLayout() {
        // get the layout
        ExpandableParticipantLayout sectionLinearLayout = (ExpandableParticipantLayout) findViewById(R.id.el_event);
        // set renderers for parent and child views
        sectionLinearLayout.setRenderer(new ExpandableParticipantLayout.Renderer<Participant, Constraint>() {
            @Override
            public void renderParent(View view, Participant model, boolean isExpanded, int parentPosition) {
                ((TextView) view.findViewById(R.id.tvParentParticipant)).setText(model.username);
                view.findViewById(R.id.arrow).setBackgroundResource(isExpanded ? R.drawable.arrow_up: R.drawable.arrow_down);
            }

            @Override
            public void renderChild(View view, Constraint model, int parentPosition, int childPosition) {
                ((TextView) view.findViewById(R.id.constraintTV)).setText(Html.fromHtml(model.constraint));
                if (model.isFinalChild) // check if it is final child of the layout
                {
                    view.setBackgroundResource(R.drawable.expandable_bottom_border);
                }
                if (model.isSeats)
                    ((TextView) view.findViewById(R.id.constraintTV)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_event_seat_24, 0, 0, 0);
                if (model.isDriver)
                    ((TextView) view.findViewById(R.id.constraintTV)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_directions_car_24, 0, 0, 0);
                if (model.isPickUp)
                    ((TextView) view.findViewById(R.id.constraintTV)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_place_24, 0, 0, 0);

            }
        });

        // Create section for each participant. First section is the user
        Section userSection = getSection(user);
        userSection.expanded = true; // User section starts expanded
        sectionLinearLayout.addSection(userSection);
        if(participants != null) {
            Section participantSection;
            for (User participant : participants) {
                participantSection = getSection(participant);
                participantSection.expanded = false; // starts collapsed
                sectionLinearLayout.addSection(participantSection);
            }
        }

        //create listeners for expansion or collapse of the layout
        sectionLinearLayout.setExpandListener((ExpandCollapseListener.ExpandListener<Participant>) (parentIndex, parent, view) -> {
            // change arrow drawable
            view.findViewById(R.id.arrow).setBackgroundResource(R.drawable.arrow_up);
        });
        sectionLinearLayout.setCollapseListener((ExpandCollapseListener.CollapseListener<Participant>) (parentIndex, parent, view) -> {
            // change arrow drawable
            view.findViewById(R.id.arrow).setBackgroundResource(R.drawable.arrow_down);
        });
    }

    /** Create a section, to be displayed in the ExpandableLayout */
    public Section<Participant, Constraint> getSection(User u) {
        Section<Participant, Constraint> Section = new Section<>();
        Participant user = new Participant(u.getUsername());
        Section.parent = user;
        Constraint userPickUp = new Constraint("<b>Pick-up: </b>"+u.getStartAddress());
        userPickUp.isPickUp = true; // is a pick-up constraint
        Section.children.add(userPickUp);
        if (u.isDriver()) {
            Constraint userDriver = new Constraint("<b>Driver:</b> Yes");
            userDriver.isDriver = true; // is a driver constraint
            Constraint userSeats = new Constraint("<b>Empty Seats:</b> "+Integer.toString(u.getSeats()));
            userSeats.isSeats = true; // is a seta number constraint
            Section.children.add(userDriver);
            Section.children.add(userSeats);
        } else{
            Constraint userDriver = new Constraint("<b>Driver:</b> No");
            userDriver.isDriver = true; // is a driver constraint
            Section.children.add(userDriver);
        }
        Section.children.get(Section.children.size()-1).isFinalChild = true; // is the final child of the layout
        return Section;
    }
}
