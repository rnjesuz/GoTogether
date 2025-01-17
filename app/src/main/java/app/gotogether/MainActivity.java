package app.gotogether;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class MainActivity extends  AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FloatingActionMenu menuEvent;
    private ArrayList<Event> eventList = new ArrayList<>();
    private ArrayList<String> eventUIDList = new ArrayList<>();
    private ArrayList<User> participants;
    private static HashMap<Event,ArrayList<User>> eventParticipants = new HashMap<Event, ArrayList<User>>();
    private Handler mUiHandler = new Handler();
    private ArrayAdapter<Event> adapter;
    private ListView eventListView;
    private FirebaseFirestore db;
    private FirebaseAuth auth;


    /* Functionality Android methods*/

    /** Initialization of variables and fictionalises required for the execution of the activity */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // see if launched from other activity besides login
        if(this.getIntent().getExtras() != null){
            Toast.makeText(this, "Event Created",Toast.LENGTH_LONG).show();
        }
        // initialize the authenticator
        auth = FirebaseAuth.getInstance();
        // initialize the db
        db = FirebaseFirestore.getInstance();
        // required settings
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .setPersistenceEnabled(true)
                .build();
        db.setFirestoreSettings(settings);

        menuEvent = (FloatingActionMenu) findViewById(R.id.menu_event);

        final FloatingActionButton actionCreate = (FloatingActionButton) findViewById(R.id.event_create);
        actionCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CreateEvent(view);
            }
        });

        final FloatingActionButton actionJoin = (FloatingActionButton) findViewById(R.id.event_join);
        actionJoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createJoinPopUpWindow();
            }
        });

        //TODO
        // used for testing
        /*final FloatingActionButton actionTest = (FloatingActionButton) findViewById(R.id.event_test);
        actionTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createTest();
            }
        });*/

        menuEvent.setClosedOnTouchOutside(true);
        menuEvent.hideMenuButton(false);

        int delay = 750;
        mUiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                    menuEvent.showMenuButton(true);
                }
            }, delay);

        SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.green_complementary));
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeRefreshLayout.setRefreshing(true);
                (new Handler()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        fetchUpdates();
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }, 1500);
            }
        });
    }

    /** create an action bar button */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logout_menu, menu);
        menu.getItem(0).setVisible(true);
        return super.onCreateOptionsMenu(menu);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_logout:
                logOut();
                return false;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private void logOut() {
        Log.i(TAG, "User is logging out...");
        createLogoutPopUpWindow();
    }

    /* Activity methods */

    /** Method called to open an Event from the event list
     * @param event the event to be launched
     * @param participants the participants of the event */
    private void LaunchEvent(Event event, ArrayList<User> participants) {
        Intent intent = new Intent(MainActivity.this, EventActivity.class);
        // TODO read from event
        String currentUserUID = auth.getUid();
        // add the uid
        intent.putExtra("eventUID", event.getId());
        // add the title
        intent.putExtra("Title", event.getTitle());
        // add the event owner
        intent.putExtra("Owner", event.getOwner());
        // is event completed?
        boolean completed = event.isCompleted();
        if (completed){
            intent.putExtra("Completed", completed);
        }
        // add the destination
        String destination = (String) event.getDestination().get("street");
        GeoPoint destinationGP = (GeoPoint) event.getDestination().get("LatLng");
        LatLng destinationLatLng = new LatLng(destinationGP.getLatitude(), destinationGP.getLongitude());
        Bundle destinationBundle = new Bundle();
        destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
        destinationBundle.putString("destinationAddress", destination);
        intent.putExtra("Destination", destinationBundle);
        // differentiate current User from other participants
        ArrayList<User> eventParticipants = participants;
        User user = new User();
        for (Iterator<User> iterator= eventParticipants.iterator(); ((Iterator) iterator).hasNext(); ){
            User u = iterator.next();
            if (u.getId().equals(currentUserUID)){
                iterator.remove();
                user = u;
            }
        }
        /*for(User u : participants){
            if (u.getId().equals(currentUserUID)){
                eventParticipants.remove(u);
                user = u;
            }
        }*/
        // add the participants
        Bundle participantsBundle = new Bundle();
        participantsBundle.putParcelableArrayList("Participants", eventParticipants);
        // add the user
        participantsBundle.putParcelable("User", user);
        intent.putExtra("Participants", participantsBundle);
        // start
        startActivity(intent);
    }

    /** Method called to launch the CreateEventActivity */
    public void CreateEvent(View view){
        Intent intent = new Intent(MainActivity.this, CreateEventActivity.class);
        startActivity(intent);

        // Drop the menu down
        menuEvent.toggle(true);
    }

    /** Method called to launch the JoinEventActivity */
    private void JoinEvent(String identifier) {

        if (identifier.matches("")){
            Toast.makeText(MainActivity.this, "The provided identifier is an invalid one. Please try again.", Toast.LENGTH_SHORT).show();
        } else if (eventUIDList.contains(identifier)){
            Toast.makeText(MainActivity.this, "Your already taking part in this event.", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(MainActivity.this, JoinEventActivity.class);
            intent.putExtra("eventUID", identifier);
            // Get data from server
            DocumentReference eventRef = db.collection("events").document(identifier);
            eventRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Log.d(TAG, "DocumentSnapshot data: " + document.getData());

                            intent.putExtra("Title", document.getString("title"));
                            intent.putExtra("Owner", document.getString("owner"));
                            intent.putExtra("Destination", createJoinActivityDestinationBundle(document));
                            db.collection("events")
                                    .document(document.getId())
                                    .collection("participants")
                                    .get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            if (task.isSuccessful()) {
                                                intent.putExtra("Participants", createJoinActivityParticipantsBundle(task));
                                                // Drop the menu down
                                                menuEvent.toggle(true);
                                                // start the activity
                                                startActivity(intent);
                                            } else {
                                                Log.d(TAG, "Error getting documents: ", task.getException());
                                            }
                                        }
                                    });
                        } else {
                            Log.d(TAG, "No such document");
                            // Show Toast to inform of incorrect identifier
                            Toast.makeText(MainActivity.this, "The provided identifier is an invalid one. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "get failed with ", task.getException());
                    }
                }
            });
        }
    }

    private void createTest() {
        Intent intent = new Intent(MainActivity.this, CreateEvent2Activity.class);
        startActivity(intent);

        // Drop the menu down
        menuEvent.toggle(true);
    }

     /** The PopUpWindow allows the input of an identifier that identifies the desired event
      * Called when clicking the join event button */
    private void createJoinPopUpWindow() {
        RelativeLayout mConstraintLayout = (RelativeLayout) findViewById(R.id.MainActivityCL);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) MainActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);

        // Inflate the custom layout/view
        View customView = inflater.inflate(R.layout.join_activity_input_window, null);

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
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        // Set an elevation value for popup window
        // Call requires API level 21
        if (Build.VERSION.SDK_INT >= 21) {
            cPopupWindow.setElevation(5.0f);
        }

        // Find widgets inside "view".
        final EditText identifier = (EditText) customView.findViewById(R.id.activity_identifier_et);

        // Get a reference for the custom view confirm button
        Button confirmButton = (Button) customView.findViewById(R.id.activity_identifier_confirm);
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
                MainActivity.this.JoinEvent(identifier.getText().toString());

            }
        });

        // Get a reference for the custom view cancel button
        Button cancelButton = (Button) customView.findViewById(R.id.activity_identifier_cancel);
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

        // Allow the popup to be focusable to edit text
        cPopupWindow.setFocusable(true);
        // Detect a click outside the window - Dismiss is the default behaviour of outside click
        cPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
                clearDim(root);
            }
        });

        // Finally, show the popup window at the center location of root relative layout
        cPopupWindow.showAtLocation(mConstraintLayout, Gravity.CENTER, 0, 0);

        cPopupWindow.update();

        // Dim the activity
        ViewGroup root = (ViewGroup) getWindow().getDecorView().getRootView();
        applyDim(root, 0.8f);

    }

    /* Auxiliary methods */

    /** Request updates from the server and update eventList */
    private void fetchUpdates() {
        populate();
        //adapter.notifyDataSetChanged(); TODO is this needed?
    }

    /** Method to populate the Activity ListView with the user's events*/
    private void populate() {
        if (adapter!=null)
            adapter.clear();
        eventList.clear();
        eventUIDList.clear();
        eventParticipants.clear();
        // get the user and its unique identifier
        FirebaseUser fbUser = auth.getCurrentUser();
        String uid = fbUser.getUid();
        // get the events the user is participating in
        db.collection("users").document(uid)
                .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Log.i(TAG, "DocumentSnapshot data: " + document.getData());
                        List<DocumentReference> events = (List<DocumentReference>) document.get("events");
                        for (int i = 0; i < events.size(); i++) {
                            String eventID = events.get(i).getId();
                            Log.i(TAG, "events id: " + eventID);
                                // get the event info
                                // the event itself
                                DocumentReference docRef = db.collection("events").document(eventID);
                                docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                        if (task.isSuccessful()) {
                                            DocumentSnapshot document = task.getResult();
                                            if (document.exists()) {
                                                Log.i(TAG, "DocumentSnapshot data: " + document.getData());
                                                task.getResult();
                                                Event newEvent = task.getResult().toObject(Event.class);
                                                newEvent.setId(eventID);
                                                newEvent.setOwner(document.getString("owner"));
                                                newEvent.setImage("ic_launcher_round");
                                                Log.i(TAG, "new Event: " + newEvent.toString());

                                                // the participants info
                                                ArrayList<User> participants = new ArrayList<>();
                                                db.collection("events").document(eventID).collection("participants")
                                                        .get()
                                                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                                if (task.isSuccessful()) {
                                                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                                                        Log.d(TAG, document.getId() + " => " + document.getData());

                                                                        String id = document.getId();
                                                                        String username = document.getString("username");
                                                                        Map<String, Object> start = (Map<String, Object>) document.get("start");
                                                                        String startAdress = (String) start.get("street");
                                                                        GeoPoint geoPoint = (GeoPoint) start.get("LatLng");
                                                                        double latitude = geoPoint.getLatitude();
                                                                        double longitude = geoPoint.getLongitude();
                                                                        LatLng startLatLng = new LatLng(latitude , longitude);
                                                                        if (document.getBoolean("driver") ){
                                                                            participants.add(new User(id, username, startAdress, startLatLng, document.getDouble("seats").intValue()));
                                                                        } else {
                                                                            participants.add(new User(id, username, startAdress, startLatLng));
                                                                        }
                                                                        newEvent.setParticipants(participants.size());
                                                                        newEvent.setParticipantsList(participants);
                                                                    }
                                                                    eventList.add(newEvent);
                                                                    eventUIDList.add(eventID);
                                                                    eventParticipants.put(newEvent, participants);
                                                                    // initialize the adapter
                                                                    initializeAdapter();
                                                                } else {
                                                                    Log.d(TAG, "Error getting documents: ", task.getException());
                                                                }
                                                            }
                                                        });

                                                //participants = getEventParticipants(eventID);
                                            } else {
                                                Log.d(TAG, "No such document");
                                            }
                                        } else {
                                            Log.d(TAG, "get failed with ", task.getException());
                                        }
                                    }
                                });
                            }
                    } else {
                        Log.d(TAG, "No such document");
                    }
                } else {
                    Log.d(TAG, "get failed with ", task.getException());
                }
            }
        });
    }

    private void initializeAdapter() {
        // Create our new array adapter
        if(adapter == null) {
            adapter = new EventArrayAdapter(this, 0, eventList);
        }
        // Find list view
        eventListView = (ListView) findViewById(R.id.EventList);
        // Bind ListView with the custom adapter
        eventListView.setAdapter(adapter);
        eventListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Event clickedEvent = eventList.get(position);
                ArrayList<User> participants = eventParticipants.get(clickedEvent);
                Log.d(TAG, "Event clicked: "+ clickedEvent.getTitle() + "; At position: " + position);
                LaunchEvent(clickedEvent, participants);
            }
        });
    }

    /** Create a Bundle with destinations Latitude, Longitude and Address
     * @param document*/
    public Bundle createJoinActivityDestinationBundle(DocumentSnapshot document){

        Bundle destinationBundle = new Bundle();
        Map<String, Object> destination = (Map<String, Object>) document.get("destination");
        GeoPoint destinationGP = (GeoPoint) destination.get("LatLng");
        LatLng destinationLatLng = new LatLng(destinationGP.getLatitude(), destinationGP.getLongitude());
        destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
        destinationBundle.putString("destinationAddress", (String) destination.get("street") );
        return destinationBundle;
    }

    /** Create a Bundle of Users participating in an Event
     * @param   task    The query returning all the participants of the event
     * @return  A bundle with the different Users(Participants) generated from the query
     * */
    public Bundle createJoinActivityParticipantsBundle(Task<QuerySnapshot> task){
        Bundle participantsBundle = new Bundle();
        ArrayList<User> participants = new ArrayList<User>();

        for (QueryDocumentSnapshot document : task.getResult()) {
            Log.d(TAG, document.getId() + " => " + document.getData());
            User participant;
            String id = document.getId();
            String username = document.getString("username");
            Map<String, Object> start = (Map<String,Object>) document.get("start");
            String street = (String) start.get("street");
            GeoPoint startGP = (GeoPoint) start.get("LatLng");
            LatLng startLatLng = new LatLng(startGP.getLatitude(), startGP.getLongitude());
            Boolean isDriver = (Boolean) document.get("driver");
            if(isDriver) {
                int seats = ((Long) document.get("seats")).intValue();
                participant = new User(id, username, street, startLatLng, seats);
            }
            else
                participant = new User(id, username, street, startLatLng);
            participants.add(participant);
        }

        participantsBundle.putParcelableArrayList("Participants", participants);
        return participantsBundle;
    }

    /** Get latitude and longitude from the address*/
    public static LatLng getLocationFromAddress(Context context, String strAddress) {

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
            p1 = new LatLng(location.getLatitude(), location.getLongitude() );

        } catch (IOException ex) {

            ex.printStackTrace();
        }

        return p1;
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

    /**
     * On back pressed exits application
     */
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Get event data from database
        populate();
    }

    private void createLogoutPopUpWindow() {
        RelativeLayout cConstraintLayout = (RelativeLayout) findViewById(R.id.MainActivityCL);
        PopupWindow cPopupWindow;
        // Initialize a new instance of LayoutInflater service
        LayoutInflater inflater = (LayoutInflater) MainActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);

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
        String htmlTitle = "<b>Logging out...</b>";
        Spanned s = Html.fromHtml(htmlTitle);
        SpannableString sString = new SpannableString(s);
        titleText.setText(sString);

        // Get a reference for the custom view text
        TextView messageText = (TextView) customView.findViewById(R.id.tv);
        // Set text
        String htmlMsg = "Are you sure you want to logout?";
        messageText.setText(Html.fromHtml(htmlMsg));


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
                // Sign out
                AuthUI.getInstance()
                        .signOut(MainActivity.this)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            public void onComplete(@NonNull Task<Void> task) {
                                // user is now signed out
                                startActivity(new Intent(MainActivity.this, MultipleLoginActivity.class));
                                finish();
                            }
                        });

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

}