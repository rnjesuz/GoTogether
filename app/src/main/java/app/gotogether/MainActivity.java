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
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static app.gotogether.CreateEventActivity.hideKeyboard;

public class MainActivity extends  AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FloatingActionMenu menuEvent;
    private ArrayList<Event> eventList = new ArrayList<>();
    private ArrayList<User> participants;
    private static HashMap<Event,ArrayList<User>> eventParticipants = new HashMap<Event, ArrayList<User>>();
    private Handler mUiHandler = new Handler();
    private ArrayAdapter<Event> adapter;
    private ListView eventListView;

    /* Testing variables */
    String[] titles = {"Tour de France", "ComicCon", "Sombra da bananeira", "Mundial 2022", "Fuga dos Paraliticos po Deserto", "Rumo ao tetra", "VDL", "Á procura da piada", "feira do tremoço", "po tras do sol posto", "ver os animais", "coçar macacos no zoo"};
    String[] locations =    {"Cascais", "Oeiras", "Setúbal", "Sintra", "Ericeira", "Óbidos", "Entroncamento", "Amadora", "Belém, Lisboa", "Caparica", "Lisboa", "Algarve", "Porto", "Moscovo", "Vaticano", "Nova Deli", "Washington DC", "Faro", "Copenhaga", "Berlim", "Londres", "Paris", "Madrid", "Seville", "Beja", "Funchal", "Pico, Açores", "Braga", "Guarda, Portugal", "Guimarães", "Santarém", "Seia, Portugal"};
    static String[] pickup =       {"Cascais", "Oeiras", "Setúbal", "Sintra", "Ericeira", "Óbidos", "Entroncamento", "Amadora", "Belém, Lisboa", "Caparica", "Lisboa", "Algarve", "Porto", "Moscovo", "Vaticano", "Nova Deli", "Washington DC", "Faro", "Copenhaga", "Berlim", "Londres", "Paris", "Madrid", "Seville", "Beja", "Funchal", "Pico, Açores", "Braga", "Guarda, Portugal", "Guimarães", "Santarém", "Seia, Portugal"};
    static String[] names = {"Ricardo", "Margarida", "Carlos", "Anabela", "TóZé", "hunter2", "MissingNo", "Anon", "*****", "aijasus", "robot2", "robot1", "Gladis", "robot3", "robot4", "Neo", "Morpheus", "Trinity", "Agent Smith", "Gaben", "Notch", "Obama", "Goku", "Ezio", "Altair", "Rambo", "Rocky", "John Snow", "Darth JarJar", "Luke", "DumbHodor", "Hari Potter", "Ben Dover"};
    private static MainActivity context;


    /* Functionality Android methods*/

    /** Initialization of variables and fictionalises required for the execution of the activity */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // the context
        context = MainActivity.this;

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

        // Create our new array adapter
        adapter = new EventArrayAdapter(this, 0, eventList);
        // Find list view and bind it with the custom adapter
        eventListView = (ListView) findViewById(R.id.EventList);
        eventListView.setAdapter(adapter);
        eventListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Event clickedEvent = eventList.get(position);
                ArrayList<User> participants = eventParticipants.get(clickedEvent);
                Log.i("Quem sao? ",participants.toString());
                Log.i("MainActivity", "Event click: "+clickedEvent.getTitle());
                LaunchEvent(clickedEvent, participants);
            }
        });

        // TODO remove after testing
        testPopulate();

        //TODO connect to server
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
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        // user is now signed out
                        startActivity(new Intent(MainActivity.this, MultipleLoginActivity.class));
                        finish();
                    }
                });
    }


    /* Activity methods */

    /** Method called to open an Event from the event list
     * @param event the event to be launched
     * @param participants the participants of the event */
    private void LaunchEvent(Event event, ArrayList<User> participants) {
        Intent intent = new Intent(MainActivity.this, EventActivity.class);
        // add the title
        intent.putExtra("Title", event.getTitle());
        // add the destination
        String destination = event.getDestination();
        Bundle destinationBundle = new Bundle();
        LatLng destinationLatLng = getLocationFromAddress(getApplicationContext(), destination);
        destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
        destinationBundle.putString("destinationAddress", destination);
        intent.putExtra("Destination", destinationBundle);
        // add the participants
        Bundle participantsBundle = new Bundle();
        participantsBundle.putParcelableArrayList("Participants", participants);
        // add the user
        User user;
        Random random = new Random();
        String name = names[random.nextInt(names.length)];
        Log.i("Participant Name",name);
        String addr = pickup[random.nextInt(pickup.length)];
        Log.i("Participant PickUp",addr);
        boolean driver = random.nextBoolean();
        int seats = 0;
        if (driver) {
            seats = ThreadLocalRandom.current().nextInt(1, 6+1);
            user = new User(name , addr, getLocationFromAddress(context, addr), seats );
        }
        else {
            user = new User(name, addr, getLocationFromAddress(context, addr));
        }
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

        Intent intent = new Intent(MainActivity.this, JoinEventActivity.class);
        // For testing purposes only. TODO Remove!!!
        if(identifier.equals("teste")) {
            intent.putExtra("Title", "Tour de France");
            intent.putExtra("Destination", createJoinActivityDestinationTestBundle());
            intent.putExtra("Participants", createJoinActivityParticipantsTestBundle());
            startActivity(intent);
            // Drop the menu down
            menuEvent.toggle(true);
        }
        else{
            // Show Toast to inform of incorrect identifier
            Toast.makeText(this, "The provided identifier is an invalid one. Please try again.", Toast.LENGTH_SHORT).show();
        }


        // Get data from server
        // TODO
        // Generate Intent with destination and participants
        // TODO

        //startActivity(intent);
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
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);

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
    private void fetchUpdates() { /*TODO*/
        adapter.clear();
        adapter.notifyDataSetChanged();
        testPopulate();
    }

    /** Method to populate the Activity ListView with dummy events */
    private void testPopulate() {
        Random random = new Random();
        // Create our events
        int eventNum = ThreadLocalRandom.current().nextInt(1, 10 + 1);
        Log.i("Nº Eventos", Integer.toString(eventNum));
        for (int i=0; i<eventNum; i++ ) {
            int participantsNum = ThreadLocalRandom.current().nextInt(3, 7 + 1);
            Log.i("Nº Particiantes", Integer.toString(participantsNum));
            participants = new ArrayList<>();
            for(int j=0; j<participantsNum; j++) {
                User participant;
                String name = names[random.nextInt(names.length)];
                Log.i("Participant Name",name);
                String addr = pickup[random.nextInt(pickup.length)];
                Log.i("Participant PickUp",addr);
                // is driver?
                boolean driver = random.nextBoolean();
                int seats = 0;
                if (driver) {
                    seats = ThreadLocalRandom.current().nextInt(1, 6+1);
                    participant = new User(name, addr, getLocationFromAddress(this, addr), seats );
                }
                else {
                    participant = new User(names[random.nextInt(names.length)], addr, getLocationFromAddress(this, addr));
                }
                participants.add(participant);
            }
            Event event = new Event(titles[random.nextInt(titles.length)], locations[random.nextInt(locations.length)], participants.size(), "ic_launcher_round");
            event.setParticipantsList(participants);
            eventList.add(event);
            Log.i("este evento", event.toString());
            Log.i("estes participantes", participants.toString());
            eventParticipants.put(event, participants);
            Log.i("este evento 2", event.toString());
            eventParticipants.get(event);
            Log.i("estes participantes 2", eventParticipants.get(event).toString());
        }
    }

    /** Create a Bundle with destinations Latitude, Longitude and Address */
    public Bundle createJoinActivityDestinationTestBundle(){

        Bundle destinationBundle = new Bundle();
        LatLng destinationLatLng = getLocationFromAddress(getApplicationContext(), "R. Cap. Salgueiro Maia, 2725-079 Algueirão- Mem Martins, Portugal");
        destinationBundle.putParcelable("destinationLatLng", destinationLatLng);
        destinationBundle.putString("destinationAddress", "R. Cap. Salgueiro Maia, 2725-079 Algueirão- Mem Martins, Portugal");
        return destinationBundle;
    }

    /** Create a Bundle of Users participating in an Event */
    public Bundle createJoinActivityParticipantsTestBundle(){
        Bundle participantsBundle = new Bundle();
        ArrayList<User> participants = new ArrayList<User>();

        //TODO remove after testing
        // Dummy user 1
        User participant1 = new User("Participant1", "Avenida da Républica, Lisboa, Portugal", getLocationFromAddress(getApplicationContext(), "Avenida da Républica, Lisboa, Portugal"), 6);
        // Dummy user 2
        User participant2 = new User("Participant2", "Instituto Superior Técnico", getLocationFromAddress(getApplicationContext(), "Instituto Superior Técnico"), 1);
        // Dummy user 3
        User participant3 = new User("Participant3", "Algualva-Cacém", getLocationFromAddress(getApplicationContext(), "Agualva-Cacém"), 10);

        participants.add(participant1);
        participants.add(participant2);
        participants.add(participant3);
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

}