package app.gotogether;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;


import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableLayout;
import expandablelib.gotogether.Section;

import static android.support.constraint.Constraints.TAG;

public class CreateEventActivity //extends FragmentActivity implements OnMapReadyCallback
extends FragmentActivity{

    private GoogleMap mMap;
    String parents = "Do you volunteer as a Driver?";
    String destination = null;
    String start = null;
    boolean driver = false;
    int seats = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        // initialize both fragment (Start and Destination queries) for Google's Places API
        initializePlaceAutoCompleteFragments();
        // initialize an ExpandableLayout that opens if user wants to volunteer as driver
        initializeExpandableLayout();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        /*SupportMapFragment mapFragmentDestination = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapDestination);
        SupportMapFragment mapFragmentStart = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapDestination);
        mapFragmentDestination.getMapAsync(onMapReadyDestination());
        mapFragmentStart.getMapAsync(onMapReadyStart());*/
    }



    private void initializePlaceAutoCompleteFragments() {

        //the fragment for the destination
        PlaceAutocompleteFragment destinationAutocompleteFragment = (PlaceAutocompleteFragment)getFragmentManager().findFragmentById(R.id.destination_autocomplete_fragment);
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

        //the fragment for the starting location
        PlaceAutocompleteFragment startAutocompleteFragment = (PlaceAutocompleteFragment)getFragmentManager().findFragmentById(R.id.start_autocomplete_fragment);
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
    }

    private void initializeExpandableLayout() {
        // get the layout
        ExpandableLayout sectionLinearLayout = (ExpandableLayout) findViewById(R.id.el);
        // set renderers for parent and child views
        sectionLinearLayout.setRenderer(new ExpandableLayout.Renderer<Driver, SeatNumber>() {
            @Override
            public void renderParent(View view, Driver model, boolean isExpanded, int parentPosition) {
                ((TextView) view.findViewById(R.id.tvParent)).setText(model.name);
                view.findViewById(R.id.arrow).setBackgroundResource(isExpanded ? R.drawable.checkbox_fill: R.drawable.checkbox_outlined);
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
            driver = true;
        });
        sectionLinearLayout.setCollapseListener((ExpandCollapseListener.CollapseListener<Driver>) (parentIndex, parent, view) -> {
            // layout collapsed = don't want to be driver
            driver = false;
            seats = -1;
        });
    }

    // create a section to be displayed in the ExpandableLayout
    public Section<Driver, SeatNumber> getSection() {
        Section<Driver, SeatNumber> Section = new Section<>();
        Driver Driver = new Driver(parents);
        SeatNumber seat = new SeatNumber();

        Section.parent = Driver;
        Section.children.add(seat);
        Section.expanded = false;
        return Section;
    }

    /*
    Finalize event creation.
    Gather inputed data from activity fields
    Communicate with server - send data, receive event identifier
    Launch new Activity for the event
    */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void ConcludeCreation(View view){
        //Gather data
        String _destination = destination;
        String _start = start;
        boolean _driver = driver;
        int _seats = seats;
        EditText seatsText = findViewById(R.id.tvChild);
        boolean emptySeats = TextUtils.isEmpty(seatsText.getText());
        if(driver) {
            if (emptySeats) {
                _seats = 5;
            } else {
                _seats = Integer.parseInt(seatsText.getText().toString());
            }
        }

        //Talk with server
        //TODO

        //Launch new activity
        /*Intent intent = new Intent(CreateEventActivity.this, EventActivity.class);
        startActivity(intent);*/
    }

}
