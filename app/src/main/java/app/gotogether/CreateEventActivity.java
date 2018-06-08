package app.gotogether;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.view.ViewGroup.LayoutParams;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.GoogleMap;


import expandablelib.gotogether.ExpandCollapseListener;
import expandablelib.gotogether.ExpandableLayout;
import expandablelib.gotogether.Section;

import static android.support.constraint.Constraints.TAG;

public class CreateEventActivity //extends FragmentActivity implements OnMapReadyCallback{
    extends AppCompatActivity{

    private GoogleMap mMap;
    private String parents = "Do you volunteer as a Driver?";
    private String destination = null;
    private String start = null;
    private boolean isDriver = false;
    private int emptySeats = -1; // -1 = no car. >0 = how many empty seats
    private boolean confirmation;

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
            isDriver = true;
        });
        sectionLinearLayout.setCollapseListener((ExpandCollapseListener.CollapseListener<Driver>) (parentIndex, parent, view) -> {
            // layout collapsed = don't want to be driver
            isDriver = false;
            emptySeats = -1;
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
    public void concludeCreation(View view){
        //Gather data
        //String _destination = destination;
        if(destination == null){
            createTextPopUpWindow("The event's destination cannot be empty.");
            return;
        }
        //String _start = start;
        if(start == null){
            createTextPopUpWindow("Your pick-up location cannot be empty.");
            return;
        }
        //boolean _isDriver = isDriver;
        //int _emptySeats = emptySeats;
        EditText seatsText = findViewById(R.id.tvChild);
        boolean unspecifiedSeats = TextUtils.isEmpty(seatsText.getText());
        if(isDriver) {
            if (unspecifiedSeats){
                emptySeats = Integer.parseInt(seatsText.getHint().toString());
            } else {
                emptySeats = Integer.parseInt(seatsText.getText().toString());
            }
        }

        // Confirm creation
        createConfirmationPopUpWindow();
    }

    private void createConfirmationPopUpWindow() {
        ConstraintLayout mConstraintLayout = (ConstraintLayout) findViewById(R.id.cl);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.confirmation_pop_up_window,null);

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
                LayoutParams.WRAP_CONTENT
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if(Build.VERSION.SDK_INT>=21){
            cPopupWindow.setElevation(5.0f);
        }

        // Get a reference for the custom view text
        TextView messageText = (TextView)customView.findViewById(R.id.tv);
        // Set text
        if(isDriver) {
            String htmlMsg = "<b>Event destination: </b><br />"+destination+"<br /><b>Your pick-up location: </b><br />"+start+"<br /><b>Driver?</b><br />Yes<br /><b>Available seats: </b><br />"+emptySeats;
            Spanned spanned = Html.fromHtml(htmlMsg);
            SpannableString spannableString = new SpannableString(spanned);
            messageText.setText(spannableString);
        }else{
            String htmlMsg = "<b>Event destination: </b><br />"+destination+"<br /><b>Your pick-up location: </b><br />"+start+"<br /><b>Driver?</b><br />No";
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
                // Confirm
                confirmation = true;
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
                // Cancel
                confirmation = false;
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
        // Finally, show the popup window at the center location of root relative layout
        cPopupWindow.showAtLocation(mConstraintLayout, Gravity.CENTER,0,0);

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);
    }

    public void createTextPopUpWindow(String message){
        ConstraintLayout mConstraintLayout = (ConstraintLayout) findViewById(R.id.cl);
        PopupWindow mPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.simple_text_pop_up_window,null);

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
        mPopupWindow = new PopupWindow(
                customView,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if(Build.VERSION.SDK_INT>=21){
            mPopupWindow.setElevation(5.0f);
        }

        // Get a reference for the custom view text
        TextView messageText = (TextView)customView.findViewById(R.id.tv);

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
                mPopupWindow.dismiss();
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
        // Finally, show the popup window at the center location of root relative layout
        mPopupWindow.showAtLocation(mConstraintLayout, Gravity.CENTER,0,0);

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);
    }

    // Apply dim to the activity
    public void applyDim(@NonNull ViewGroup parent, float dimAmount){
        Drawable dim = new ColorDrawable(Color.BLACK);
        dim.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        dim.setAlpha((int) (255 * dimAmount));

        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.add(dim);
    }

    // Clear dim from the activity
    public void clearDim(@NonNull ViewGroup parent) {
        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.clear();
    }

    public void confirmEvent(){
        Log.d("Confirmation", "Yup");
        //Talk with server
        //TODO send info. receive token
        //TODO make pop-up to confirm input

        //Launch new activity
        /*Intent intent = new Intent(CreateEventActivity.this, EventActivity.class);
        startActivity(intent);*/
    }

}
